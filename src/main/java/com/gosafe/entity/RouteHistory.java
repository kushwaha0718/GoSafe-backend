package com.gosafe.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "route_history")
@Data
@NoArgsConstructor
public class RouteHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(length = 300, nullable = false)
    private String origin;

    @Column(length = 300, nullable = false)
    private String destination;

    @Column(name = "route_name", length = 120)
    private String routeName;

    @Column(length = 30)
    private String distance;

    @Column(length = 30)
    private String duration;

    @Column(name = "safety_score")
    private Integer safetyScore;

    @CreationTimestamp
    @Column(name = "searched_at", updatable = false)
    private LocalDateTime searchedAt;
}
