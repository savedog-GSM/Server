package com.example.savedog.Owner.service;

import com.example.savedog.Navigation.DTO.NavigationResponse;
import com.example.savedog.Navigation.domain.NavigationPurpose;
import com.example.savedog.Navigation.domain.NavigationStatus;
import com.example.savedog.Navigation.service.RobotNavigationService;
import com.example.savedog.Owner.DTO.ReturnToOwnerResponse;
import com.example.savedog.Pathfinding.domain.PathMode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 놓친 로보독이 주인 폰 위치로 돌아오게 한다. 실제 주행은 RobotNavigationService가 한다. (안전 경로 + 얼굴 확인)
 * 로보독은 한 대라 목적지 안내 중에 시작하면 그것을 그만두고 돌아온다.
 */
@Service
@RequiredArgsConstructor
public class ReturnToOwnerService {
    private final RobotNavigationService navigation;

    // 주인 위치를 받아 시작한다. 이미 돌아가는 중이면 주인 위치만 바꾼다. (주인 폰이 몇 초마다 다시 보내면 된다)
    public ReturnToOwnerResponse start(Double latitude, Double longitude, Double accuracy) {
        return toResponse(navigation.start(NavigationPurpose.OWNER, latitude, longitude, accuracy, PathMode.SAFEST));
    }

    // 주인에게 돌아가는 중일 때만 멈춘다. (목적지 안내는 건드리지 않는다)
    public ReturnToOwnerResponse stop() {
        return toResponse(navigation.stop(NavigationPurpose.OWNER));
    }

    public ReturnToOwnerResponse snapshot() {
        return toResponse(navigation.snapshot());
    }

    private static ReturnToOwnerResponse toResponse(NavigationResponse state) {
        if (state.purpose() != NavigationPurpose.OWNER) {
            String message = state.active()
                    ? "지금은 목적지로 이동 중입니다. 주인에게 돌아가기를 시작하면 그것을 멈추고 돌아옵니다."
                    : "주인에게 돌아가기가 꺼져 있습니다.";
            return new ReturnToOwnerResponse(false, NavigationStatus.IDLE, message,
                    null, null, null, state.robot(), null, List.of(), null, null, null, null);
        }
        return new ReturnToOwnerResponse(
                state.active(),
                state.status(),
                state.message(),
                state.target(),
                state.targetAccuracy(),
                state.targetUpdatedAt(),
                state.robot(),
                state.distanceMeters(),
                state.plannedPath() == null ? List.of() : state.plannedPath().path(),
                state.lastCommand(),
                state.lastCommandAt(),
                state.startedAt(),
                state.finishedAt()
        );
    }
}
