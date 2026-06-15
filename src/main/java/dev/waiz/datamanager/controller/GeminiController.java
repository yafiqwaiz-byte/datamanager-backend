package dev.waiz.datamanager.controller;

import java.util.*;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.waiz.datamanager.service.GeminiService;
import dev.waiz.datamanager.service.ProcessedRowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/ai"
)
@RequiredArgsConstructor
@Slf4j
public class GeminiController {

    private final GeminiService geminiService;
    private final ProcessedRowService processedRowService;

    
    @GetMapping("/analyze/{excelId}")
    public ResponseEntity<?> analyzeDataset(@PathVariable UUID excelId){
        try{
            List<Map<String,Object>> sampleRows = processedRowService.getSampleRows(excelId,3);

            log.info("Sample rows count:{}", sampleRows.size());

            if (sampleRows.isEmpty()) {
                return ResponseEntity.badRequest().body("No data found for this Excel file");
            }

            List<String> columns = new ArrayList<>(sampleRows.get(0).keySet());
            log.info("Columns found:{}",columns);

            Map<String,Object> analysis = geminiService.analyzeAndSuggestDashboard(columns, sampleRows);

            if (analysis.containsKey("error")){
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(analysis);
            }

            return ResponseEntity.ok(analysis);
        } catch (RuntimeException e){
            log.error("Analysis failed:{}",e.getMessage());

            if (e.getMessage() != null && e.getMessage().contains("429")) {
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of(
                    "error",true,
                    "message","AI service is busy,Please wait 1 minute and try again."
                ));
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "error",true,
                "message",e.getMessage() != null ? e.getMessage():"Analysis failed"
            ));
        
        } catch (Exception e){
            log.error("Analysis failed:{}",e.getMessage(),e);
            return ResponseEntity.status(500).body("Analysis failed:" + e.getMessage());
        }
    }
    
}
