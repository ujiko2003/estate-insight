package com.hy.estate.region;

public record RegionResponse(
        String sggCode,
        String sidoName,
        String sggName,
        Boolean isActive) {
    public static RegionResponse from(Region region) {
        return new RegionResponse(
                region.getSggCode(),
                region.getSidoName(),
                region.getSggName(),
                region.getIsActive());
    }
}
