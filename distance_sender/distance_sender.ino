// 지켜멍 거리 센서 (ESP32 + 초음파 센서 HC-SR04)
// 1초마다 전방 거리를 재서 서버의 센서 값 전송 API(POST /api/robot/telemetry)로 보낸다.
//
// 배선
//   HC-SR04 VCC  → ESP32 5V(VIN)
//   HC-SR04 GND  → ESP32 GND
//   HC-SR04 TRIG → GPIO 5
//   HC-SR04 ECHO → GPIO 18
//   ※ ECHO는 5V 신호라 그대로 꽂으면 ESP32 핀이 망가질 수 있다.
//     저항 두 개(예: 1kΩ, 2kΩ)로 나눠 3.3V로 낮춰서 연결하거나, 3.3V용 HC-SR04P를 쓴다.
//
// 준비
//   1) Arduino IDE 보드 매니저에서 "esp32"를 설치하고 ESP32 Dev Module을 고른다. 추가 라이브러리는 필요 없다.
//   2) 이 폴더의 secrets.example.h를 secrets.h로 복사하고 Wi-Fi·토큰 값을 채운다. (secrets.h는 git에 올라가지 않는다)
//   3) 업로드 후 시리얼 모니터(115200)에서 "거리 ...cm 보냄"이 1초마다 찍히면 정상이다.

#include <WiFi.h>
#include <WiFiClientSecure.h>
#include <HTTPClient.h>
#include "secrets.h"  // WIFI_SSID, WIFI_PASSWORD, DEVICE_TOKEN

// ───────── 설정 ─────────
// 학교 서버(HTTPS). 인증서 문제로 안 되면 "http://service.gsmsv.site:22107/api/robot/telemetry"를 쓴다.
const char* SERVER_URL = "https://savedog.https.gsmsv.site/api/robot/telemetry";

const int TRIG_PIN = 5;
const int ECHO_PIN = 18;
const unsigned long SEND_INTERVAL_MS = 1000;  // 거리를 보내는 간격
// ────────────────────────

const float MAX_DISTANCE_CM = 400.0;           // 서버가 받는 최대 거리. HC-SR04의 측정 한계와 같다.
const unsigned long ECHO_TIMEOUT_US = 30000;   // 이만큼 기다려도 반사가 없으면 4m 안에 장애물이 없는 것으로 본다.
const float CM_PER_MICROSECOND = 0.0343;       // 소리가 1마이크로초에 가는 거리
const int SAMPLES = 3;                         // 튀는 값을 줄이려고 세 번 재서 가운데 값을 쓴다.
const unsigned long RECONNECT_INTERVAL_MS = 5000;
const int HTTP_TIMEOUT_MS = 3000;

// HTTPS 연결을 매번 새로 맺으면 1초 안에 못 보낼 수 있어서 연결을 계속 쓴다.
WiFiClientSecure secureClient;
WiFiClient plainClient;
HTTPClient http;
bool useHttps = false;

unsigned long lastSentAt = 0;
unsigned long lastReconnectAt = 0;

void setup() {
  Serial.begin(115200);
  pinMode(TRIG_PIN, OUTPUT);
  pinMode(ECHO_PIN, INPUT);
  digitalWrite(TRIG_PIN, LOW);

  useHttps = strncmp(SERVER_URL, "https://", 8) == 0;
  // 프로토타입이라 서버 인증서를 확인하지 않는다. (암호화는 된다)
  // 실제로 쓸 때는 setCACert()로 인증서를 넣어 가짜 서버에 토큰을 보내지 않게 한다.
  secureClient.setInsecure();
  http.setReuse(true);
  http.setConnectTimeout(HTTP_TIMEOUT_MS);
  http.setTimeout(HTTP_TIMEOUT_MS);

  WiFi.mode(WIFI_STA);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  Serial.println("Wi-Fi에 연결하는 중...");
}

void loop() {
  unsigned long now = millis();
  if (now - lastSentAt >= SEND_INTERVAL_MS) {
    lastSentAt = now;
    tick(now);
  }
  delay(5);
}

void tick(unsigned long now) {
  if (WiFi.status() != WL_CONNECTED) {
    // 연결을 시도하는 도중에 다시 부르면 처음부터 다시 하게 되므로 몇 초에 한 번만 다시 붙는다.
    if (now - lastReconnectAt >= RECONNECT_INTERVAL_MS) {
      lastReconnectAt = now;
      Serial.println("Wi-Fi가 끊겨 있어 다시 연결합니다.");
      WiFi.reconnect();
    }
    return;
  }
  sendDistance(measureDistance());
}

// 한 번 잰 전방 거리(cm). 반사가 돌아오지 않으면 최대 거리로 본다.
float measureOnce() {
  digitalWrite(TRIG_PIN, LOW);
  delayMicroseconds(2);
  digitalWrite(TRIG_PIN, HIGH);
  delayMicroseconds(10);
  digitalWrite(TRIG_PIN, LOW);

  unsigned long duration = pulseIn(ECHO_PIN, HIGH, ECHO_TIMEOUT_US);
  if (duration == 0) {
    return MAX_DISTANCE_CM;
  }
  // 소리가 갔다가 돌아온 시간이므로 2로 나눈다.
  float distance = duration * CM_PER_MICROSECOND / 2.0;
  return distance > MAX_DISTANCE_CM ? MAX_DISTANCE_CM : distance;
}

// 세 번 재서 가운데 값을 돌려준다.
float measureDistance() {
  float values[SAMPLES];
  for (int i = 0; i < SAMPLES; i++) {
    values[i] = measureOnce();
    delay(10);  // 앞 측정의 반사음이 사라질 때까지 기다린다.
  }
  for (int i = 0; i < SAMPLES - 1; i++) {
    for (int j = 0; j < SAMPLES - 1 - i; j++) {
      if (values[j] > values[j + 1]) {
        float swap = values[j];
        values[j] = values[j + 1];
        values[j + 1] = swap;
      }
    }
  }
  return values[SAMPLES / 2];
}

void sendDistance(float distance) {
  bool started = useHttps ? http.begin(secureClient, SERVER_URL) : http.begin(plainClient, SERVER_URL);
  if (!started) {
    Serial.println("서버 주소(SERVER_URL)를 확인하세요.");
    return;
  }
  http.addHeader("Content-Type", "application/json");
  if (strlen(DEVICE_TOKEN) > 0) {
    http.addHeader("X-Device-Token", DEVICE_TOKEN);
  }

  char body[48];
  snprintf(body, sizeof(body), "{\"sensor\":{\"distance\":%.1f}}", distance);
  int status = http.POST(body);

  if (status == 201) {
    Serial.printf("거리 %.1fcm 보냄\n", distance);
  } else if (status > 0) {
    // 400(값 오류), 401(토큰 오류) 등. 서버가 이유를 JSON으로 알려 준다.
    Serial.printf("서버가 받지 않았습니다 (%d): %s\n", status, http.getString().c_str());
  } else {
    Serial.printf("서버에 보내지 못했습니다: %s\n", http.errorToString(status).c_str());
  }
  http.end();
}
