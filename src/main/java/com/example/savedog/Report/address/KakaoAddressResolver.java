package com.example.savedog.Report.address;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Optional;

// 카카오 로컬 API의 "좌표로 주소 변환하기"를 쓴다. 실패해도 신고 생성은 계속되도록 빈 값을 돌려준다.
@Slf4j
public class KakaoAddressResolver implements AddressResolver {
    private static final String BASE_URL = "https://dapi.kakao.com";
    private static final Duration TIMEOUT = Duration.ofSeconds(3);

    private final RestClient restClient;
    private final JsonMapper jsonMapper;

    public KakaoAddressResolver(String restApiKey, JsonMapper jsonMapper) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(TIMEOUT).build()
        );
        requestFactory.setReadTimeout(TIMEOUT);
        this.restClient = RestClient.builder()
                .baseUrl(BASE_URL)
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "KakaoAK " + restApiKey)
                .build();
        this.jsonMapper = jsonMapper;
    }

    @Override
    public Optional<String> resolve(double latitude, double longitude) {
        try {
            // 카카오는 x가 경도, y가 위도다.
            String body = restClient.get()
                    .uri(uri -> uri.path("/v2/local/geo/coord2address.json")
                            .queryParam("x", longitude)
                            .queryParam("y", latitude)
                            .build())
                    .retrieve()
                    .body(String.class);
            return body == null ? Optional.empty() : parse(body);
        } catch (RuntimeException e) {
            // 통신 오류, 응답 해석 오류뿐 아니라 어떤 실패든 신고 생성까지 막아서는 안 된다.
            log.warn("주소 변환 실패 ({}, {}): {}", latitude, longitude, e.toString());
            return Optional.empty();
        }
    }

    // 도로명 주소가 있으면 그것을, 없으면 지번 주소를 쓴다.
    private Optional<String> parse(String body) {
        JsonNode document = jsonMapper.readTree(body).path("documents").path(0);
        return addressName(document.path("road_address"))
                .or(() -> addressName(document.path("address")));
    }

    private Optional<String> addressName(JsonNode address) {
        JsonNode name = address.path("address_name");
        if (!name.isString() || name.stringValue().isBlank()) {
            return Optional.empty();
        }
        return Optional.of(name.stringValue());
    }
}
