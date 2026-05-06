package dev.waiz.datamanager.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import dev.waiz.datamanager.model.ocrresult;

public interface OcrResultRepository extends JpaRepository<ocrresult,UUID>{

    Optional<ocrresult> findByUpload_UploadId(UUID uploadId);

}
