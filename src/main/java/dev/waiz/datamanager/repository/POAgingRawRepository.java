package dev.waiz.datamanager.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import dev.waiz.datamanager.model.poagingraw;

public interface POAgingRawRepository extends JpaRepository<poagingraw,UUID> {

    List<poagingraw> findByReport_ReportId(UUID reportId);

    List<poagingraw> findByPoNumberIn(List<String> poNumber);

    List<poagingraw> findByIsClearedFalse();

   
}
