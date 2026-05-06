package dev.waiz.datamanager.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import dev.waiz.datamanager.model.exceldata;
import dev.waiz.datamanager.service.DataPreprocessingService;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/excel")
public class DataPreprocessingController {

    private final DataPreprocessingService dataPreprocessingService;


    public ResponseEntity<?> processData(@PathVariable UUID uploadId,@RequestParam("file") MultipartFile file){
        try {
            exceldata result = dataPreprocessingService.processExcelData(uploadId, file);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Failed to process data Excel:"+ e.getMessage());
        }
    }

    public ResponseEntity<List<exceldata>> getByUploadId(@PathVariable UUID uploadId){
        return ResponseEntity.ok(dataPreprocessingService.getByUploadId(uploadId));
    }
}
