package com.example.savedog.Pathfinding.controller;

import com.example.savedog.Pathfinding.DTO.PathSearchRequest;
import com.example.savedog.Pathfinding.DTO.PathSearchResponse;
import com.example.savedog.Pathfinding.service.PathfindingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/paths")
@RequiredArgsConstructor
public class PathfindingController {
    private final PathfindingService pathfindingService;

    @PostMapping("/search")
    public PathSearchResponse search(@RequestBody PathSearchRequest request) {
        return pathfindingService.search(request);
    }
}
