package com.gosafe.repository;

import com.gosafe.entity.RouteHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RouteHistoryRepository extends JpaRepository<RouteHistory, Long> {
    List<RouteHistory> findTop20ByUserIdOrderBySearchedAtDesc(Long userId);
}
