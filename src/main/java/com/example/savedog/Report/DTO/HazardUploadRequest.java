package com.example.savedog.Report.DTO;

// 중계 프로그램이 multipart로 보낸 위험 신고 후보. image는 JPEG 바이트, 없으면 null
// hazardType: GENERAL_DANGER, FIRE, GROUND_DAMAGE / robotId·source·qrCode는 기록용(로보독은 한 대만 다룬다)
public record HazardUploadRequest(
        String hazardType,
        String robotId,
        String source,
        String qrCode,
        byte[] image
) {
}
