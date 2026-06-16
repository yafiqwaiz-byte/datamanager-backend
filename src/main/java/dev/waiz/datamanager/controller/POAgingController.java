package dev.waiz.datamanager.controller;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import dev.waiz.datamanager.dto.POAgingDashboardDTO;
import dev.waiz.datamanager.service.POAgingService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/po-aging")
@RequiredArgsConstructor
public class POAgingController {

    private final POAgingService poAgingService;
   

    @PostMapping("/upload/raw/{uploadId}")
    public ResponseEntity<?> uploadRawPOData(@RequestParam("file") MultipartFile file,@PathVariable UUID uploadId){
        try{
            POAgingDashboardDTO result = poAgingService.processRawPOData(file,uploadId);

            return ResponseEntity.ok(result);
        } catch (Exception e){
            return ResponseEntity.status(500).body(Map.of("error",e.getMessage()));
        }
    }

    // Upload cleared PO file
    @PostMapping("/upload/cleared/{uploadId}")
    public ResponseEntity<?> uploadClearedPO(
            @RequestParam("file") MultipartFile file,
            @PathVariable UUID uploadId) {
        try {
            POAgingDashboardDTO result = 
                poAgingService.processClearedPOFile(file, uploadId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    // Get dashboard
    @GetMapping("/dashboard/{uploadId}")
    public ResponseEntity<?> getDashboard(@PathVariable UUID uploadId) {
        try {
            POAgingDashboardDTO result = 
                poAgingService.getDashboardByUploadId(uploadId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("error", e.getMessage()));
        }
    }

}
