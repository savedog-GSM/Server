package com.example.savedog.support;

import com.example.savedog.global.exception.BusinessException;
import com.example.savedog.global.exception.ErrorCode;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public final class Errors {
    private Errors() {
    }

    // 실행하면 주어진 오류 코드의 BusinessException이 나는지 확인하고, 그 예외를 돌려준다.
    public static BusinessException assertError(ErrorCode expected, Executable action) {
        BusinessException e = assertThrows(BusinessException.class, action);
        assertEquals(expected, e.getErrorCode(), e.getMessage());
        return e;
    }
}
