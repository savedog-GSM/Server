package com.example.savedog.Report.address;

import java.util.Optional;

// 좌표를 사람이 읽는 주소로 바꾼다. 못 구하면 Optional.empty() (예외를 던지지 않는다)
public interface AddressResolver {
    Optional<String> resolve(double latitude, double longitude);
}
