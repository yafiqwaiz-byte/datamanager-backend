package dev.waiz.datamanager.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.waiz.datamanager.model.processedrows;
import dev.waiz.datamanager.service.ProcessedRowService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/processed-rows")
@RequiredArgsConstructor
public class ProcessedRowsController {

    private final ProcessedRowService processedRowService;

    @PostMapping("/save/{excelId}")
    public ResponseEntity<?> saveRows(@PathVariable UUID excelId){
        try{
            List<processedrows> rows = processedRowService.saveProcessedRows(excelId);
            return ResponseEntity.ok(rows);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Failed to save processed rows: " + e.getMessage());
        }
    }

    @GetMapping("/{excelId}/all")
    public ResponseEntity<List<processedrows>> getAllRows(@PathVariable UUID excelId,@RequestParam(required =false) String version){
        return ResponseEntity.ok(processedRowService.getRowsByExcelId(excelId,version));
    }

    @GetMapping("/{excelId}/raw")
    public ResponseEntity<List<processedrows>> getRawRows(@PathVariable UUID excelId){
        return ResponseEntity.ok(processedRowService.getRowsByVersion(excelId,"raw"));
    }


    @GetMapping("/{excelId}/cleaned")
    public ResponseEntity<List<processedrows>> getCleanedRows(@PathVariable UUID excelId){
        return ResponseEntity.ok(processedRowService.getRowsByVersion(excelId,"cleaned"));
    }
}
