package dev.waiz.datamanager.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import dev.waiz.datamanager.model.processedrows;

public interface ProcessedRowsRepository extends JpaRepository<processedrows,UUID> {

    List<processedrows> findByExcel_ExcelId(UUID excelId);
    List<processedrows> findByExcel_ExcelIdAndDataVersion(UUID excelId,String dataVersion);

}
