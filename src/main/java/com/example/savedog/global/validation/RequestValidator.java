package com.example.savedog.global.validation;

import com.example.savedog.global.common.GeoPoint;
import com.example.savedog.global.exception.BusinessException;
import com.example.savedog.global.exception.ErrorCode;

// 요청 값 검사. 어긋나면 무엇이 잘못됐는지 적은 400(INVALID_REQUEST)을 던진다.
public final class RequestValidator {
    private static final double MAX_LATITUDE = 90;
    private static final double MAX_LONGITUDE = 180;

    private RequestValidator() {
    }

    public static <T> T required(T value, String name) {
        if (value == null) {
            throw invalid(name + " 값이 필요합니다.");
        }
        return value;
    }

    public static double inRange(Double value, double min, double max, String name) {
        required(value, name);
        // NaN은 어떤 비교에도 걸리지 않으므로 따로 막는다.
        if (!Double.isFinite(value) || value < min || value > max) {
            throw invalid("%s 값은 %s 이상 %s 이하여야 합니다.".formatted(name, plain(min), plain(max)));
        }
        return value;
    }

    public static int inRange(Integer value, int min, int max, String name) {
        required(value, name);
        if (value < min || value > max) {
            throw invalid("%s 값은 %d 이상 %d 이하여야 합니다.".formatted(name, min, max));
        }
        return value;
    }

    public static double nonNegative(Double value, String name) {
        required(value, name);
        if (!Double.isFinite(value) || value < 0) {
            throw invalid(name + " 값은 0 이상이어야 합니다.");
        }
        return value;
    }

    public static GeoPoint coordinate(Double latitude, Double longitude, String name) {
        return new GeoPoint(
                inRange(latitude, -MAX_LATITUDE, MAX_LATITUDE, name + ".latitude"),
                inRange(longitude, -MAX_LONGITUDE, MAX_LONGITUDE, name + ".longitude")
        );
    }

    public static BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.INVALID_REQUEST, message);
    }

    // 400.0 대신 400으로 보이게 한다.
    private static String plain(double number) {
        return number == Math.rint(number) ? String.valueOf((long) number) : String.valueOf(number);
    }
}
