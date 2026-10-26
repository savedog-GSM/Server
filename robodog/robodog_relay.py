# 지켜멍 로보독 중계 프로그램
#
# 로보독은 블루투스 동글로만 PC와 통신하고 서버에 직접 접속하지 못한다.
# 이 프로그램이 그 사이를 이어 준다. Thonny에서 열어 실행(F5)하면 된다.
#   - 서버가 보낸 명령(전진, 멈추기, 앉기, 짖기 등)을 받아 로보독을 움직이고 결과를 돌려준다.
#   - 로보독의 기울기(roll, pitch)를 1초마다 서버로 보낸다. (거리는 아두이노가 따로 보낸다.)
#
# 필요한 패키지 (Thonny: 도구 > 패키지 관리): kocoafab_robodog, websockets, opencv-python
# 카메라는 기본으로 이 PC의 웹캠 0번을 1초마다 올린다. 다른 카메라는 CAMERA_SOURCE 환경 변수로 지정한다.
# 카메라 업로드를 끄려면 CAMERA_ENABLED=0, 서버 주소를 바꾸려면 SAVEDOG_SERVER 환경 변수를 지정한다.
# 끝낼 때는 셸에서 Ctrl+C를 누른다. 그래야 로보독을 멈추고 연결을 닫은 뒤 끝난다.

# 라이브러리가 내보내는 이름이 아래 모듈 이름을 가리지 않도록 가장 먼저 불러온다.
from kocoafab_robodog import *  # RoboDog, FORWARD, CW, SITDOWN, 멍멍, 크게 ...

import json
import os
import queue
import threading
import time
import urllib.error
import urllib.request
from datetime import datetime

from websockets.exceptions import ConnectionClosed
from websockets.sync.client import connect

# ───────── 설정 ─────────
# SAVEDOG_SERVER에는 localhost:8080, http://192.168.0.10:8080 또는 https://도메인 을 지정할 수 있다.
SERVER = os.environ.get("SAVEDOG_SERVER", "localhost:8080").strip().rstrip("/")
if SERVER.startswith("https://"):
    API_BASE_URL = SERVER
    WEBSOCKET_URL = "wss://" + SERVER[len("https://"):]
elif SERVER.startswith("http://"):
    API_BASE_URL = SERVER
    WEBSOCKET_URL = "ws://" + SERVER[len("http://"):]
else:
    API_BASE_URL = "http://" + SERVER
    WEBSOCKET_URL = "ws://" + SERVER

DEVICE_TOKEN = os.environ.get("DEVICE_API_TOKEN", "")  # 서버에 DEVICE_API_TOKEN을 설정했으면 같은 값. 안 했으면 빈 문자열
ROBODOG_PORT = None             # 로보독 동글 포트. None이면 자동으로 찾고, 못 찾으면 "COM3" 처럼 직접 적는다.

DEFAULT_SPEED = 50              # 명령에 속도가 없을 때 쓰는 속도 (0~100)
LEG_BEND = 60                   # 시작할 때 맞추는 다리 굽힘 (40~75가 안정적). None이면 건드리지 않는다.
TILT_INTERVAL_SECONDS = 1.0     # 기울기를 보내는 간격
CAMERA_ENABLED = os.environ.get("CAMERA_ENABLED", "1").strip().lower() not in ("0", "false", "no", "off")
CAMERA_SOURCE_VALUE = os.environ.get("CAMERA_SOURCE", "0").strip()  # USB 웹캠 번호 또는 OpenCV 호환 스트림 URL
CAMERA_SOURCE = int(CAMERA_SOURCE_VALUE) if CAMERA_SOURCE_VALUE.lstrip("-").isdigit() else CAMERA_SOURCE_VALUE
CAMERA_INTERVAL_SECONDS = max(0.2, float(os.environ.get("CAMERA_INTERVAL_SECONDS", "1.0")))
CAMERA_WIDTH = int(os.environ.get("CAMERA_WIDTH", "640"))
CAMERA_HEIGHT = int(os.environ.get("CAMERA_HEIGHT", "480"))
CAMERA_JPEG_QUALITY = max(30, min(95, int(os.environ.get("CAMERA_JPEG_QUALITY", "80"))))
MAX_FRAME_BYTES = 2 * 1024 * 1024
GESTURE_SECONDS = 2.0           # 앉기·일어서기 같은 동작이 끝나기를 기다리는 시간 (최소 2초)
RECONNECT_SECONDS = 5.0         # 서버와 끊겼을 때 다시 접속하기까지 기다리는 시간

# 좌회전·우회전 방향. 실제 로보독이 반대로 돌면 두 값을 서로 바꾼다.
LEFT_SPIN = CCW
RIGHT_SPIN = CW
# ────────────────────────

# 이 프로그램은 기다릴 때 time.sleep()을 쓰지 않는다.
# 라이브러리가 sleep() 뒤에 이동을 자동으로 멈추도록 되어 있어서, 다른 스레드의 sleep이 로보독을 세울 수 있기 때문이다.
# 대신 이 이벤트를 기다린다. 프로그램을 끝낼 때 set() 하면 모든 대기가 바로 풀린다.
stopping = threading.Event()


def log(message):
    print(datetime.now().strftime("%H:%M:%S"), message, flush=True)


def describe(error):
    return "{}: {}".format(type(error).__name__, error)


class Robot:
    """로보독에 보내는 동작을 한곳에 모은다."""

    def __init__(self, dog):
        self.dog = dog
        self.sitting = False

    def stop_moving(self):
        self.dog.move(STOP)
        self.dog.spin(STOP)

    def move(self, direction, speed):
        self.stop_moving()
        self._stand_up_if_sitting()
        self.dog.move(direction, speed)

    def spin(self, direction, speed):
        self.stop_moving()
        self._stand_up_if_sitting()
        self.dog.spin(direction, speed)

    def sit(self):
        self.stop_moving()
        self.dog.gesture(SITDOWN)
        self.sitting = True
        stopping.wait(GESTURE_SECONDS)

    def bark(self):
        self.dog.sound(멍멍, 크게)

    def tilt(self):
        """(roll, pitch). 로보독이 아직 값을 주지 않았으면 None"""
        value = self.dog.get_tilt()
        if value is None or len(value) < 2:
            return None
        roll, pitch = float(value[0]), float(value[1])
        if not (-180 <= roll <= 180 and -180 <= pitch <= 180):
            raise ValueError("기울기 값이 ±180°를 벗어났습니다: roll={}, pitch={}".format(roll, pitch))
        return roll, pitch

    def _stand_up_if_sitting(self):
        # 앉은 자세에서는 걷지 못하므로 먼저 일으켜 세운다.
        if self.sitting:
            self.dog.gesture(STANDUP)
            stopping.wait(GESTURE_SECONDS)
            self.sitting = False


class CommandWorker(threading.Thread):
    """서버가 보낸 명령을 받은 순서대로 하나씩 실행한다."""

    def __init__(self, robot, send_result):
        super().__init__(daemon=True)
        self.robot = robot
        self.send_result = send_result
        self.commands = queue.Queue()

    def add(self, command):
        self.commands.put(command)

    def clear(self):
        """아직 실행하지 않은 명령을 버린다. 서버와 끊긴 뒤에 묵은 명령이 실행되지 않게 한다."""
        while True:
            try:
                self.commands.get_nowait()
            except queue.Empty:
                return

    def run(self):
        stop_at = None  # 시간이 정해진 이동을 멈출 시각. 그런 이동이 없으면 None
        while not stopping.is_set():
            command = self._next(0.5 if stop_at is None else min(0.5, max(0.0, stop_at - time.monotonic())))
            if command is None:
                if stop_at is not None and time.monotonic() >= stop_at:
                    self._stop_moving()
                    stop_at = None
                continue
            try:
                changes_motion, seconds = self._execute(command)
                result = "OK"
            except Exception as error:  # 로보독 쪽 오류로 프로그램 전체가 죽지 않게 한다.
                # 어디까지 실행됐는지 알 수 없으므로 일단 세운다.
                changes_motion, seconds = True, None
                self._stop_moving()
                result = describe(error)
                log("명령 {} 실행 실패: {}".format(command.get("type"), result))
            if changes_motion:
                stop_at = None if seconds is None else time.monotonic() + seconds
            self.send_result(command.get("commandId"), result)

    def _next(self, timeout):
        try:
            return self.commands.get(timeout=timeout)
        except queue.Empty:
            return None

    def _execute(self, command):
        """명령을 실행하고 (이동 상태를 바꿨는지, 이동을 유지할 시간(초))을 돌려준다. 시간이 None이면 다음 명령까지 유지한다."""
        kind = command.get("type")
        speed = command.get("speed")
        speed = DEFAULT_SPEED if speed is None else max(0, min(100, int(speed)))
        duration = command.get("durationMs")
        seconds = None if duration is None else max(0.0, float(duration) / 1000)

        if kind == "BARK":
            # 짖는 소리는 길이가 정해져 있어 시간(durationMs)을 따로 맞추지 않는다. 걷는 중이면 걸으면서 짖는다.
            log("명령 BARK")
            self.robot.bark()
            return False, None
        if kind == "STOP":
            log("명령 STOP")
            self.robot.stop_moving()
            return True, None
        if kind == "SIT":
            log("명령 SIT")
            self.robot.sit()
            return True, None

        log("명령 {} (속도 {}, {})".format(kind, speed, "다음 명령까지" if seconds is None else "{}초".format(seconds)))
        if kind == "FORWARD":
            self.robot.move(FORWARD, speed)
        elif kind == "BACKWARD":
            self.robot.move(BACKWARD, speed)
        elif kind == "TURN_LEFT":
            self.robot.spin(LEFT_SPIN, speed)
        elif kind == "TURN_RIGHT":
            self.robot.spin(RIGHT_SPIN, speed)
        else:
            raise ValueError("모르는 명령입니다: {}".format(kind))
        return True, seconds

    def _stop_moving(self):
        try:
            self.robot.stop_moving()
        except Exception as error:
            log("로보독을 멈추지 못했습니다: {}".format(describe(error)))


class TiltSender(threading.Thread):
    """로보독의 기울기를 일정 간격으로 서버에 보낸다."""

    def __init__(self, robot):
        super().__init__(daemon=True)
        self.robot = robot
        self.problem = None  # 같은 문제를 1초마다 되풀이해 찍지 않으려고 마지막 상태를 기억한다.

    def run(self):
        while not stopping.wait(TILT_INTERVAL_SECONDS):
            self._report(self._send_once())

    def _send_once(self):
        """문제없이 보냈으면 None, 아니면 무엇이 문제인지 적은 문장"""
        try:
            tilt = self.robot.tilt()
        except Exception as error:
            return "로보독에서 기울기를 읽지 못했습니다 ({})".format(describe(error))
        if tilt is None:
            return "로보독이 아직 기울기 값을 주지 않습니다."

        body = json.dumps({"sensor": {"roll": tilt[0], "pitch": tilt[1]}}).encode("utf-8")
        headers = {"Content-Type": "application/json"}
        if DEVICE_TOKEN:
            headers["X-Device-Token"] = DEVICE_TOKEN
        request = urllib.request.Request(
            API_BASE_URL + "/api/robot/telemetry", data=body, headers=headers, method="POST"
        )
        try:
            with urllib.request.urlopen(request, timeout=3) as response:
                response.read()
            return None
        except urllib.error.HTTPError as error:
            return "서버가 기울기를 받지 않았습니다 ({} {})".format(error.code, server_message(error))
        except OSError as error:  # 서버가 꺼져 있거나 주소가 틀린 경우
            return "서버에 기울기를 보내지 못했습니다 ({})".format(error)

    def _report(self, problem):
        if problem == self.problem:
            return
        log("기울기를 다시 정상으로 보내고 있습니다." if problem is None else problem)
        self.problem = problem


class CameraSender(threading.Thread):
    """웹캠에서 JPEG를 캡처해 서버에 주기적으로 올린다."""

    def __init__(self):
        super().__init__(daemon=True)
        self.problem = None

    def run(self):
        try:
            import cv2
        except Exception as error:
            log("카메라 업로드를 시작할 수 없습니다 (opencv-python 설치 필요: {}).".format(describe(error)))
            return

        camera = None
        try:
            while not stopping.is_set():
                started_at = time.monotonic()
                problem = None
                try:
                    if camera is None or not camera.isOpened():
                        if camera is not None:
                            camera.release()
                        camera = cv2.VideoCapture(CAMERA_SOURCE)
                        camera.set(cv2.CAP_PROP_FRAME_WIDTH, CAMERA_WIDTH)
                        camera.set(cv2.CAP_PROP_FRAME_HEIGHT, CAMERA_HEIGHT)
                        if not camera.isOpened():
                            camera.release()
                            camera = None
                            problem = "카메라를 열지 못했습니다 (CAMERA_SOURCE={} 확인).".format(CAMERA_SOURCE)

                    if camera is not None:
                        captured, frame = camera.read()
                        if not captured or frame is None:
                            camera.release()
                            camera = None
                            problem = "카메라 프레임을 읽지 못했습니다. 카메라 연결을 확인하세요."
                        else:
                            problem = self._send_frame(cv2, frame)
                except Exception as error:
                    problem = "카메라 전송 오류: {}".format(describe(error))

                self._report(problem)
                remaining = CAMERA_INTERVAL_SECONDS - (time.monotonic() - started_at)
                if stopping.wait(max(0.0, remaining)):
                    break
        finally:
            if camera is not None:
                camera.release()

    def _send_frame(self, cv2, frame):
        jpeg = None
        qualities = dict.fromkeys((CAMERA_JPEG_QUALITY, 65, 50, 35))
        for quality in qualities:
            encoded, buffer = cv2.imencode(".jpg", frame, [cv2.IMWRITE_JPEG_QUALITY, quality])
            if not encoded:
                return "카메라 프레임을 JPEG로 변환하지 못했습니다."
            jpeg = buffer.tobytes()
            if len(jpeg) <= MAX_FRAME_BYTES:
                break
        if jpeg is None or len(jpeg) > MAX_FRAME_BYTES:
            return "JPEG 프레임이 서버 제한(2MB)을 넘습니다. CAMERA_WIDTH/HEIGHT를 낮추세요."

        headers = {"Content-Type": "image/jpeg"}
        if DEVICE_TOKEN:
            headers["X-Device-Token"] = DEVICE_TOKEN
        request = urllib.request.Request(
            API_BASE_URL + "/api/robot/frames", data=jpeg, headers=headers, method="POST"
        )
        try:
            with urllib.request.urlopen(request, timeout=5) as response:
                if not 200 <= response.status < 300:
                    return "서버가 카메라 사진을 받지 않았습니다 (HTTP {}).".format(response.status)
            return None
        except urllib.error.HTTPError as error:
            hint = " 장치 토큰(DEVICE_API_TOKEN)을 확인하세요." if error.code == 401 else ""
            return "서버가 카메라 사진을 받지 않았습니다 ({} {}).{}".format(
                error.code, server_message(error), hint
            ).strip()
        except OSError as error:
            return "서버에 카메라 사진을 보내지 못했습니다 ({}).".format(error)

    def _report(self, problem):
        if problem == self.problem:
            return
        if problem is None:
            log("카메라 프레임 전송 정상 ({}초 간격).".format(CAMERA_INTERVAL_SECONDS))
        else:
            log(problem)
        self.problem = problem


def server_message(error):
    try:
        return json.loads(error.read().decode("utf-8")).get("message", "")
    except Exception:
        return ""


class ServerLink:
    """서버의 로보독 명령 채널(/ws/robot)에 붙어 있다가 명령이 오면 넘겨준다. 끊기면 다시 붙는다."""

    def __init__(self, robot):
        self.robot = robot
        self.socket = None
        self.send_lock = threading.Lock()
        self.worker = CommandWorker(robot, self.send_result)

    def send_result(self, command_id, result):
        if command_id is None:
            return
        with self.send_lock:
            socket = self.socket
            if socket is None:
                return
            try:
                socket.send(json.dumps({"commandId": command_id, "result": result}))
            except Exception:
                pass  # 그 사이 끊겼으면 결과를 보낼 곳이 없다. 다시 접속하는 것은 run()이 한다.

    def run(self):
        self.worker.start()
        url = WEBSOCKET_URL + "/ws/robot"
        headers = {"X-Device-Token": DEVICE_TOKEN} if DEVICE_TOKEN else None
        while not stopping.is_set():
            try:
                with connect(url, additional_headers=headers, open_timeout=5) as socket:
                    self.socket = socket
                    log("서버에 연결했습니다: {}".format(url))
                    while True:
                        try:
                            # 짧게 끊어 기다려야 Windows에서 Ctrl+C가 바로 먹는다.
                            message = socket.recv(timeout=1)
                        except TimeoutError:
                            continue
                        self._receive(message)
            except ConnectionClosed:
                log("서버와 연결이 끊겼습니다.")
            except Exception as error:  # 서버가 꺼져 있음, 주소가 틀림, 토큰이 틀림 등
                hint = " (DEVICE_TOKEN이 서버 설정과 같은지 확인하세요.)" if "401" in str(error) else ""
                log("서버에 연결하지 못했습니다: {}{}".format(describe(error), hint))
            finally:
                self.socket = None
                # 서버와 끊긴 동안에는 멈추라는 명령도 받을 수 없으므로, 밀린 명령은 버리고 일단 세운다.
                self.worker.clear()
                try:
                    self.robot.stop_moving()
                except Exception as error:
                    log("로보독을 멈추지 못했습니다: {}".format(describe(error)))
            self._pause(RECONNECT_SECONDS)

    def _pause(self, seconds):
        # 한 번에 길게 기다리지 않는 이유는 위의 recv와 같다.
        until = time.monotonic() + seconds
        while not stopping.is_set() and time.monotonic() < until:
            stopping.wait(0.5)

    def _receive(self, message):
        try:
            command = json.loads(message)
        except (TypeError, ValueError):
            log("서버가 보낸 메시지를 해석하지 못했습니다: {!r}".format(message))
            return
        if not isinstance(command, dict) or "type" not in command:
            log("명령이 아닌 메시지를 받았습니다: {!r}".format(message))
            return
        self.worker.add(command)


def main():
    dog = RoboDog()
    opened = dog.Open() if ROBODOG_PORT is None else dog.Open(ROBODOG_PORT)
    if not opened:
        log("로보독에 연결하지 못했습니다. 동글이 꽂혀 있고 로보독 전원이 켜져 있는지 확인하세요.")
        return
    log("로보독에 연결했습니다.")
    robot = Robot(dog)
    camera_sender = None
    try:
        if LEG_BEND is not None:
            dog.leg_bend(ALL_LEG, LEG_BEND)
        TiltSender(robot).start()
        if CAMERA_ENABLED:
            camera_sender = CameraSender()
            camera_sender.start()
            log("카메라 업로드를 시작합니다 (소스 {}, {}초 간격).".format(CAMERA_SOURCE, CAMERA_INTERVAL_SECONDS))
        ServerLink(robot).run()
    except KeyboardInterrupt:
        log("종료합니다.")
    finally:
        stopping.set()
        try:
            if camera_sender is not None:
                camera_sender.join(timeout=2)
            robot.stop_moving()
        finally:
            dog.Close()


if __name__ == "__main__":
    main()
