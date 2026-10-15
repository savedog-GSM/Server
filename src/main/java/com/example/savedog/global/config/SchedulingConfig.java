package com.example.savedog.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Configuration
@EnableScheduling
public class SchedulingConfig {
    private static final int WAITING_REPORTS = 10;

    /**
     * 신고 만들기 전용 작업 스레드 하나.
     * 신고를 만들 때는 외부 서비스(주소 변환, S3)를 부르므로 느릴 수 있는데,
     * 그 때문에 센서 값 수신이 늦어지지 않도록 따로 돌린다.
     * 대기 줄이 가득 차면 새 작업을 버린다. 같은 위험이면 어차피 한 건으로 합쳐질 신고다.
     */
    @Bean(destroyMethod = "shutdown")
    public ExecutorService hazardReportExecutor() {
        return new ThreadPoolExecutor(
                1, 1, 0, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(WAITING_REPORTS),
                runnable -> {
                    Thread thread = new Thread(runnable, "hazard-report");
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.DiscardPolicy()
        );
    }
}
