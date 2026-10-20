package com.example.savedog.Robot.domain;

public enum CommandType {
    FORWARD,
    BACKWARD,
    TURN_LEFT,
    TURN_RIGHT,
    STOP,
    BARK,
    SIT,
    // 멈춘 뒤 RESUME을 받을 때까지 이동·회전을 거부한다. (중계 프로그램의 안전 잠금)
    EMERGENCY_STOP,
    // 안전 잠금을 풀고 전진한다.
    RESUME
}
