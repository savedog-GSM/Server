package com.example.savedog.Owner.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.LocalDateTime;

/**
 * 주인 얼굴 하나의 특징값(숫자 128개). 사진은 저장하지 않는다.
 * 특징값만으로 원래 얼굴 사진을 되살릴 수는 없지만 생체 정보이므로 다 쓰면 지운다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OwnerFace {
    private static final int EMBEDDING_BYTES = 128 * Float.BYTES;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // float 128개를 리틀 엔디언으로 이어 붙인 값
    @Column(nullable = false, length = EMBEDDING_BYTES)
    private byte[] embedding;

    private LocalDateTime createdAt;

    public OwnerFace(float[] embedding, LocalDateTime createdAt) {
        ByteBuffer buffer = ByteBuffer.allocate(embedding.length * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        for (float value : embedding) {
            buffer.putFloat(value);
        }
        this.embedding = buffer.array();
        this.createdAt = createdAt;
    }

    public float[] embeddingValues() {
        ByteBuffer buffer = ByteBuffer.wrap(embedding).order(ByteOrder.LITTLE_ENDIAN);
        float[] values = new float[embedding.length / Float.BYTES];
        for (int i = 0; i < values.length; i++) {
            values[i] = buffer.getFloat();
        }
        return values;
    }
}
