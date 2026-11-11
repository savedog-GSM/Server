package com.example.savedog.Owner.service;

import com.example.savedog.Owner.DTO.OwnerFaceDeleteResponse;
import com.example.savedog.Owner.DTO.OwnerFaceResponse;
import com.example.savedog.Owner.domain.OwnerFace;
import com.example.savedog.Owner.face.FaceDetection;
import com.example.savedog.Owner.face.FaceEngine;
import com.example.savedog.Owner.repository.OwnerFaceRepository;
import com.example.savedog.global.exception.BusinessException;
import com.example.savedog.global.exception.ErrorCode;
import com.example.savedog.global.validation.RequestValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 주인 얼굴 등록·삭제. 사진에서 얼굴 하나를 찾아 특징값만 저장한다. (사진은 저장하지 않는다)
 * 등록된 얼굴은 로보독이 주인을 다시 찾아갈 때 카메라 사진 속 얼굴과 비교하는 데 쓴다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OwnerFaceService {
    // 정면·약간 옆·안경 유무처럼 여러 장을 올리면 더 잘 알아본다. 너무 많이 쌓이지 않게 막는다.
    static final int MAX_FACES = 10;
    // 이보다 작게 찍힌 얼굴은 특징값이 흐려 비교가 부정확하다.
    static final double MIN_FACE_WIDTH_PIXELS = 60;

    private final FaceEngine faceEngine;
    private final OwnerFaceRepository ownerFaceRepository;
    private final Clock clock;

    // 동시에 두 장을 올려도 개수 제한을 넘지 않게 한 번에 하나씩 등록한다.
    @Transactional
    public synchronized OwnerFaceResponse register(MultipartFile image) {
        if (!faceEngine.available()) {
            throw new BusinessException(ErrorCode.FACE_MODEL_UNAVAILABLE,
                    "얼굴 인식 모델 파일이 없어 등록할 수 없습니다. 서버의 models 폴더를 확인해 주세요.");
        }
        BufferedImage photo = decode(image);
        long registered = ownerFaceRepository.count();
        if (registered >= MAX_FACES) {
            throw new BusinessException(ErrorCode.OWNER_FACE_LIMIT,
                    "얼굴은 %d장까지 등록할 수 있습니다. 필요 없는 얼굴을 지운 뒤 다시 올려 주세요.".formatted(MAX_FACES));
        }

        FaceDetection face = onlyFace(photo);
        float[] embedding = run(() -> faceEngine.embed(photo, face));
        List<OwnerFace> others = ownerFaceRepository.findAll();
        Double similarity = others.isEmpty() ? null : others.stream()
                .mapToDouble(other -> FaceEngine.similarity(embedding, other.embeddingValues()))
                .average()
                .orElseThrow();
        // 이미 등록된 얼굴과 다른 사람이면 받지 않는다. 다른 사람을 주인으로 알아보면 안 되기 때문이다.
        if (similarity != null && similarity < FaceEngine.SAME_PERSON_SIMILARITY) {
            throw RequestValidator.invalid(
                    "이미 등록된 얼굴과 다른 사람으로 보입니다. (유사도 %.2f) 주인 얼굴만 올리거나, 전부 지운 뒤 다시 등록해 주세요."
                            .formatted(similarity));
        }

        OwnerFace saved = ownerFaceRepository.save(new OwnerFace(embedding, LocalDateTime.now(clock)));
        return new OwnerFaceResponse(saved.getId(), round(face.score()), similarity == null ? null : round(similarity),
                registered + 1, saved.getCreatedAt());
    }

    // id를 주면 그 얼굴만, 안 주면 등록된 얼굴을 모두 지운다.
    @Transactional
    public synchronized OwnerFaceDeleteResponse delete(Long id) {
        if (id == null) {
            long deleted = ownerFaceRepository.count();
            ownerFaceRepository.deleteAllInBatch();
            return new OwnerFaceDeleteResponse(deleted, 0);
        }
        if (!ownerFaceRepository.existsById(id)) {
            throw new BusinessException(ErrorCode.OWNER_FACE_NOT_FOUND);
        }
        ownerFaceRepository.deleteById(id);
        return new OwnerFaceDeleteResponse(1, ownerFaceRepository.count());
    }

    private BufferedImage decode(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw RequestValidator.invalid("image 항목에 얼굴 사진을 넣어 주세요.");
        }
        try {
            BufferedImage photo = ImageIO.read(new ByteArrayInputStream(image.getBytes()));
            if (photo == null) {
                throw RequestValidator.invalid("JPEG·PNG 사진만 올릴 수 있습니다.");
            }
            return photo;
        } catch (IOException e) {
            throw RequestValidator.invalid("사진을 읽지 못했습니다. 파일이 깨졌는지 확인해 주세요.");
        }
    }

    private FaceDetection onlyFace(BufferedImage photo) {
        List<FaceDetection> faces = run(() -> faceEngine.detect(photo));
        if (faces.isEmpty()) {
            throw new BusinessException(ErrorCode.FACE_NOT_FOUND,
                    "사진에서 얼굴을 찾지 못했습니다. 밝은 곳에서 얼굴 정면이 잘 보이게 찍어 주세요.");
        }
        if (faces.size() > 1) {
            throw RequestValidator.invalid("사진에 얼굴이 %d개 있습니다. 주인 한 명만 나오게 찍어 주세요.".formatted(faces.size()));
        }
        FaceDetection face = faces.getFirst();
        if (face.width() < MIN_FACE_WIDTH_PIXELS) {
            throw RequestValidator.invalid("얼굴이 너무 작게 찍혔습니다. 얼굴이 사진의 큰 부분을 차지하도록 가까이서 찍어 주세요.");
        }
        return face;
    }

    // 모델 실행 중 오류는 요청 잘못이 아니라 서버 쪽 문제다.
    private <T> T run(java.util.function.Supplier<T> action) {
        try {
            return action.get();
        } catch (BusinessException e) {
            throw e;
        } catch (RuntimeException e) {
            log.error("얼굴 인식 모델 실행 실패", e);
            throw new BusinessException(ErrorCode.FACE_MODEL_UNAVAILABLE, "얼굴 인식 중 오류가 났습니다. 잠시 뒤 다시 시도해 주세요.", e);
        }
    }

    private static double round(double value) {
        return Math.round(value * 1000) / 1000.0;
    }
}
