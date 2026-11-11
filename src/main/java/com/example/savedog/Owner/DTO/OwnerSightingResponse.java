package com.example.savedog.Owner.DTO;

import com.example.savedog.Owner.service.OwnerSighting;

/**
 * 지금 카메라에 주인이 보이는지.
 * available: 얼굴 모델이 켜져 있는지, registeredFaces: 등록된 주인 얼굴 수
 * fresh: 결과가 3초 안에 받은 사진의 것인지 (false면 사진이 끊긴 것이라 ownerVisible을 믿으면 안 된다)
 * sighting: 가장 최근 분석 결과. 아직 분석한 사진이 없으면 null
 */
public record OwnerSightingResponse(
        boolean available,
        long registeredFaces,
        boolean fresh,
        boolean ownerVisible,
        OwnerSighting sighting
) {
}
