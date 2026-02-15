package com.gosafe.repository;

import com.gosafe.entity.SavedRoute;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface SavedRouteRepository extends JpaRepository<SavedRoute, Long> {
    List<SavedRoute> findByUserIdOrderBySavedAtDesc(Long userId);
    Optional<SavedRoute> findByIdAndUserId(Long id, Long userId);
}
