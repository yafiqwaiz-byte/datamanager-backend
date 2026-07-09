package dev.waiz.datamanager.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import dev.waiz.datamanager.model.activitylog;

public interface ActivityLogRepository extends JpaRepository<activitylog, UUID> {

    List<activitylog> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
