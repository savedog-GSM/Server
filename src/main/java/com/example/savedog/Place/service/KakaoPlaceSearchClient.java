package com.example.savedog.Place.service;

import com.example.savedog.global.exception.BusinessException;
import com.example.savedog.global.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class KakaoPlaceSearchClient {
    private static final String BASE_URL = "https://dapi.kakao.com";
    private static final Duration TIMEOUT = Duration.ofSeconds(4);

    private final String restApiKey;
    private final RestClient restClient;
    private final JsonMapper jsonMapper;

    public KakaoPlaceSearchClient(String restApiKey, JsonMapper jsonMapper) {
        this(BASE_URL, restApiKey, jsonMapper);
    }

    KakaoPlaceSearchClient(String baseUrl, String restApiKey, JsonMapper jsonMapper) {
        this.restApiKey = restApiKey == null ? "" : restApiKey.trim();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(TIMEOUT).build()
        );
        requestFactory.setReadTimeout(TIMEOUT);
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "KakaoAK " + this.restApiKey)
                .build();
        this.jsonMapper = jsonMapper;
    }

    KakaoPlaceSearchResult searchCategory(
            String categoryCode, double longitude, double latitude, int radiusMeters, int page, int size
    ) {
        return search("/v2/local/search/category.json", "category_group_code", categoryCode,
                longitude, latitude, radiusMeters, page, size);
    }

    KakaoPlaceSearchResult searchKeyword(
            String query, double longitude, double latitude, int radiusMeters, int page, int size
    ) {
        return search("/v2/local/search/keyword.json", "query", query,
                longitude, latitude, radiusMeters, page, size);
    }

    private KakaoPlaceSearchResult search(
            String path, String searchParameter, String searchValue,
            double longitude, double latitude, int radiusMeters, int page, int size
    ) {
        if (restApiKey.isBlank()) {
            throw new BusinessException(ErrorCode.KAKAO_API_KEY_MISSING);
        }
        try {
            String body = restClient.get()
                    .uri(uri -> uri.path(path)
                            .queryParam(searchParameter, searchValue)
                            // Kakao uses x=longitude, y=latitude.
                            .queryParam("x", longitude)
                            .queryParam("y", latitude)
                            .queryParam("radius", radiusMeters)
                            .queryParam("sort", "distance")
                            .queryParam("page", page)
                            .queryParam("size", size)
                            .build())
                    .retrieve()
                    .body(String.class);
            if (body == null) {
                throw new IllegalStateException("Kakao returned an empty response.");
            }
            return parse(body);
        } catch (RuntimeException e) {
            log.warn("Kakao place search failed: {}", e.toString());
            throw new BusinessException(ErrorCode.KAKAO_PLACE_SEARCH_FAILED,
                    ErrorCode.KAKAO_PLACE_SEARCH_FAILED.getMessage(), e);
        }
    }

    private KakaoPlaceSearchResult parse(String body) {
        JsonNode root = jsonMapper.readTree(body);
        JsonNode documents = root.path("documents");
        List<KakaoPlace> places = new ArrayList<>();
        for (JsonNode document : documents) {
            places.add(new KakaoPlace(
                    string(document, "id"),
                    string(document, "place_name"),
                    string(document, "category_name"),
                    string(document, "category_group_code"),
                    string(document, "category_group_name"),
                    string(document, "phone"),
                    string(document, "address_name"),
                    string(document, "road_address_name"),
                    number(document, "y"),
                    number(document, "x"),
                    optionalNumber(document, "distance"),
                    string(document, "place_url")
            ));
        }
        JsonNode isEnd = root.path("meta").path("is_end");
        boolean hasMore = isEnd.isBoolean() && !isEnd.booleanValue();
        return new KakaoPlaceSearchResult(List.copyOf(places), hasMore);
    }

    private String string(JsonNode document, String field) {
        JsonNode value = document.path(field);
        return value.isString() && !value.stringValue().isBlank() ? value.stringValue() : null;
    }

    private double number(JsonNode document, String field) {
        String value = string(document, field);
        if (value == null) {
            throw new IllegalStateException("Kakao place result is missing coordinate " + field);
        }
        return Double.parseDouble(value);
    }

    private Double optionalNumber(JsonNode document, String field) {
        String value = string(document, field);
        return value == null ? null : Double.valueOf(value);
    }
}
