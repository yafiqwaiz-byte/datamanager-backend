package dev.waiz.datamanager.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import dev.waiz.datamanager.model.poagingreport;

public interface POAgingReportRepository extends JpaRepository<poagingreport,UUID>{

    List<poagingreport> findByUpload_UploadId(UUID uploadId);

    // Get latest report
    @Query("SELECT p FROM poagingreport p ORDER BY p.createdAt DESC")
    List<poagingreport> findLatestReports();

    List<poagingreport> findBySubzone(String subzone);

    List<poagingreport> findByMarks(Integer marks);

    List<poagingreport> findByUpdatedMarks(Integer marks);
}
