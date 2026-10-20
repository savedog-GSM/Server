-- 기울기를 NOT NULL로 저장하던 예전 버전(df24159)이 만든 telemetry 테이블과 그때의 기록 한 건.
-- H2의 INIT로 연결을 열 때마다 실행되므로 여러 번 돌아도 같은 결과가 되게 쓴다.
CREATE TABLE IF NOT EXISTS telemetry (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    distance DOUBLE PRECISION NOT NULL,
    latitude DOUBLE PRECISION,
    location_accuracy DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    pitch DOUBLE PRECISION NOT NULL,
    recorded_at TIMESTAMP,
    roll DOUBLE PRECISION NOT NULL,
    speed DOUBLE PRECISION
);
INSERT INTO telemetry (distance, roll, pitch, recorded_at)
SELECT 150, 1.5, -2, TIMESTAMP '2026-10-01 12:00:00' WHERE NOT EXISTS (SELECT 1 FROM telemetry);
