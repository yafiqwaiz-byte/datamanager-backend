package dev.waiz.datamanager.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import dev.waiz.datamanager.model.processeddata;

public interface ProcessedDataRepository extends JpaRepository<processeddata,UUID> {

    Optional<processeddata> findByUpload_UploadId(UUID uploadId);
}
