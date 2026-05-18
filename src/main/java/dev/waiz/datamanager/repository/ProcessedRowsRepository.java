package dev.waiz.datamanager.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page; 
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import dev.waiz.datamanager.model.processedrows;

public interface ProcessedRowsRepository extends JpaRepository<processedrows,UUID> {

    List<processedrows> findByExcel_ExcelId(UUID excelId);

    @Query("""
            SELECT p FROM processedrows p
            JOIN FETCH p.excel e
            WHERE e.excelId = :excelId
            AND p.dataVersion = :dataVersion
            """)
    Page<processedrows> findByExcel_ExcelIdAndDataVersion(@Param("excelId")UUID excelId,@Param("dataVersion")String dataVersion,Pageable pageable);

}
