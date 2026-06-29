package dev.waiz.datamanager.controller;

import dev.waiz.datamanager.dto.POAgingDashboardDTO;
import dev.waiz.datamanager.service.GeminiService;
import dev.waiz.datamanager.service.POAgingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/gemini")
@RequiredArgsConstructor
public class GeminiController {

    private final GeminiService geminiService;
    private final POAgingService poAgingService;
    private final ObjectMapper objectMapper;

    /**
     * Analyze the PO Aging dashboard for a specific upload.
     * Returns management-level interpretation, risk highlights,
     * critical stations, and recommended actions.
     *
     * POST /api/gemini/analyze/po-aging/{uploadId}
     */
    @PostMapping("/analyze/po-aging/{uploadId}")
    public ResponseEntity<?> analyzePOAging(
            @PathVariable UUID uploadId) {
        try {
            // Get dashboard data for this upload
            POAgingDashboardDTO dashboard =
                poAgingService.getDashboardByUploadId(uploadId);

            // Convert DTO to Map for Gemini prompt injection
            @SuppressWarnings("unchecked")
            Map<String, Object> dashboardMap =
                objectMapper.convertValue(dashboard, Map.class);

            // Call Gemini
            Map<String, Object> analysis =
                geminiService.analyzePOAgingDashboard(dashboardMap);

            // Check if Gemini returned an error
            if (analysis.containsKey("error")) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of(
                            "error", true,
                            "message", analysis.get("error")
                        ));
            }

            return ResponseEntity.ok(analysis);

        } catch (RuntimeException e) {
            // 429 rate limit
            if (e.getMessage() != null &&
                e.getMessage().contains("busy")) {
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .body(Map.of(
                            "error", true,
                            "message", e.getMessage()
                        ));
            }
            log.error("PO Aging analysis failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                        "error", true,
                        "message", "Analysis failed: " + e.getMessage()
                    ));
        } catch (Exception e) {
            log.error("PO Aging analysis error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                        "error", true,
                        "message", "Analysis failed: " + e.getMessage()
                    ));
        }
    }

    /**
     * Analyze the latest PO Aging dashboard (no uploadId needed).
     * Convenience endpoint for the frontend.
     *
     * POST /api/gemini/analyze/po-aging/latest
     */
    @PostMapping("/analyze/po-aging/latest")
    public ResponseEntity<?> analyzeLatestPOAging() {
        try {
            POAgingDashboardDTO dashboard =
                poAgingService.getLatestDashboard();

            @SuppressWarnings("unchecked")
            Map<String, Object> dashboardMap =
                objectMapper.convertValue(dashboard, Map.class);

            Map<String, Object> analysis =
                geminiService.analyzePOAgingDashboard(dashboardMap);

            if (analysis.containsKey("error")) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("error", true, "message", analysis.get("error")));
            }

            return ResponseEntity.ok(analysis);

        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("busy")) {
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .body(Map.of("error", true, "message", e.getMessage()));
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", true,
                                 "message", "Analysis failed: " + e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", true,
                                 "message", "Analysis failed: " + e.getMessage()));
        }
    }
}