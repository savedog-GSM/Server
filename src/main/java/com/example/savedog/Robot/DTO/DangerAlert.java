package com.example.savedog.Robot.DTO;

import com.example.savedog.Telemetry.DTO.TelemetryResponse;

/**
 * 위험도가 5(위험)로 바뀌었을 때 관제 화면에 띄울 알림. (기능명세서 F-16)
 * message는 화면에 그대로 보여 줄 수 있는 문장이다.
 * robotStopped: 로보독에 멈추기·앉기·짖기를 보냈으면 true. 끊겨 있어 보내지 못했으면 false
 */
public record DangerAlert(
        String message,
        boolean robotStopped,
        TelemetryResponse telemetry
) {
    public static DangerAlert of(TelemetryResponse telemetry, boolean robotStopped) {
        String reason = telemetry.risk() == null ? null : telemetry.risk().reason();
        String detected = reason == null ? "위험이 감지되었습니다." : "위험 감지: " + reason + ".";
        String response = robotStopped
                ? "로보독을 멈춰 앉히고 짖게 했습니다."
                : "로보독에 멈춤 신호를 보내지 못했습니다.";
        return new DangerAlert(detected + " " + response, robotStopped, telemetry);
    }
}
