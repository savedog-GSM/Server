package com.example.savedog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

@SpringBootApplication
public class SavedogApplication {
	// 시각은 시간대 없이(예: 2026-10-20T15:30:00) 내려주고 화면은 한국 시각으로 읽는다.
	// 도커 이미지의 기본 시간대(UTC)를 따르면 9시간 늦은 시각이 찍히므로 한국 시간으로 고정한다.
	public static final String ZONE = "Asia/Seoul";

	public static void main(String[] args) {
		TimeZone.setDefault(TimeZone.getTimeZone(ZONE));
		SpringApplication.run(SavedogApplication.class, args);
	}

}
