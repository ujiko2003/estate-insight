package com.hy.estate.region;

import jakarta.persistence.*;
import lombok.Getter;

@Entity
@Getter
@Table(name = "region")
public class Region {

    @Id
    @Column(length = 5)
    private String sggCode;

    @Column(nullable = false)
    private String sidoName;

    @Column(nullable = false)
    private String sggName;

    @Column(nullable = false)
    private Boolean isActive;

    // 기본 생성자
    public Region() {}

}