package com.example.savedog.Owner.service;

// 주인에게 돌아가기의 현재 상태
public enum ReturnStatus {
    // 시작 전이거나 중지됨
    IDLE,
    // 로보독 위치(폰 GPS)를 기다리는 중. 멈춰 있다.
    WAITING_FOR_ROBOT_LOCATION,
    // 처음 몇 m를 앞으로 가서 로보독이 바라보는 방향을 알아내는 중
    FINDING_HEADING,
    // 경로를 따라 가는 중
    NAVIGATING,
    // 다음 지점 쪽으로 도는 중
    TURNING,
    // 카메라에 주인 얼굴이 보여 얼굴을 보고 다가가는 중
    APPROACHING_BY_FACE,
    // 앞에 장애물이 있어(위험 등급) 멈춰서 기다리는 중
    BLOCKED_BY_OBSTACLE,
    // 위험 칸을 피해 갈 경로가 없어 멈춰서 기다리는 중 (30초마다 다시 찾는다)
    NO_SAFE_ROUTE,
    // 로보독이 서버에 연결되어 있지 않아 명령을 못 보내는 중
    ROBOT_DISCONNECTED,
    // 주인 곁에 도착 (멈추고 짖음)
    ARRIVED,
    // 너무 오래 걸려 그만둠
    GAVE_UP
}
