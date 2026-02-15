package com.gosafe.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "saved_routes")
@Data
@NoArgsConstructor
public class SavedRoute {

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

    @Column(name = "route_data", columnDefinition = "JSON")
    private String routeData;

    @Column(length = 100)
    private String label;

    @CreationTimestamp
    @Column(name = "saved_at", updatable = false)
    private LocalDateTime savedAt;
}
