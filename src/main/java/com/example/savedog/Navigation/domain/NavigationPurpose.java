package com.example.savedog.Navigation.domain;

// 로보독을 왜 움직이는지. 로보독은 한 대라 한 번에 하나만 한다.
public enum NavigationPurpose {
    // 지정한 목적지까지 안전 경로로 간다.
    DESTINATION("목적지"),
    // 놓친 로보독이 주인 폰 위치로 돌아온다. 카메라에 주인 얼굴이 보이면 얼굴을 보고 다가간다.
    OWNER("주인");

    private final String label;

    NavigationPurpose(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
