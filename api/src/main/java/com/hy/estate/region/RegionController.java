package com.hy.estate.region;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/regions")
public class RegionController {

    private final RegionRepository regionRepository;

    @GetMapping
    public List<RegionResponse> getAllRegions() {
        return regionRepository.findAll().stream()
                .map(RegionResponse::from)
                .toList();
    }
}
