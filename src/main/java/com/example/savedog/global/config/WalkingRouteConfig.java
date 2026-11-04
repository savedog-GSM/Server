package com.example.savedog.global.config;

import com.example.savedog.Pathfinding.osm.OsrmWalkingRouteSource;
import com.example.savedog.Pathfinding.osm.WalkingRouteSource;
import com.example.savedog.global.common.GeoPoint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

// 도로를 따라가는 걷기 경로(OpenStreetMap). 끄면 도로 정보 없이 칸으로만 경로를 찾는다.
@Slf4j
@Configuration
public class WalkingRouteConfig {

    @Bean
    public WalkingRouteSource walkingRouteSource(
            @Value("${savedog.osm.routing-enabled:true}") boolean enabled,
            @Value("${savedog.osm.routing-url:https://routing.openstreetmap.de/routed-foot}") String routingUrl,
            JsonMapper jsonMapper
    ) {
        if (!enabled || routingUrl.isBlank()) {
            log.warn("걷기 경로 서버를 쓰지 않아 경로가 도로를 따라가지 않습니다. (OSM_ROUTING_ENABLED)");
            return new WalkingRouteSource() {
                @Override
                public List<List<GeoPoint>> routes(List<GeoPoint> waypoints, boolean alternatives) {
                    throw new WalkingRouteUnavailableException("걷기 경로 서버를 쓰지 않도록 설정했습니다.", null);
                }

                @Override
                public boolean available() {
                    return false;
                }
            };
        }
        return new OsrmWalkingRouteSource(routingUrl, jsonMapper);
    }
}
