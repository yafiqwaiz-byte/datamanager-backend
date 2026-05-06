package dev.waiz.datamanager.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import dev.waiz.datamanager.model.exceldata;

public interface ExcelDataRepository extends JpaRepository<exceldata,UUID>{
    List<exceldata> findByUpload_UploadId(UUID uploadID);

}
