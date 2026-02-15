package com.gosafe.repository;

import com.gosafe.entity.EmergencyContact;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface EmergencyContactRepository extends JpaRepository<EmergencyContact, Long> {
    List<EmergencyContact> findByUserIdOrderByIdAsc(Long userId);
    long countByUserId(Long userId);
    void deleteByIdAndUserId(Long id, Long userId);
}
