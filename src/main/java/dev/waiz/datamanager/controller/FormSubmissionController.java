package dev.waiz.datamanager.controller;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import dev.waiz.datamanager.dto.SubmissionResponseDTO;
import dev.waiz.datamanager.service.FormSubmissionService;

@RestController
@RequestMapping("/api/forms")
public class FormSubmissionController {

    @Autowired
    private FormSubmissionService formSubmissionService;

    @PostMapping(value = "/submit", consumes = "multipart/form-data")
    public ResponseEntity<?> submit(
            @RequestParam("templateId") UUID templateId,
            @RequestParam("inputMethod") String inputMethod,
            @RequestParam Map<String, String> allParams,
            @RequestParam(required = false) Map<String, MultipartFile> allFiles) {
        try {
            UUID submissionId = formSubmissionService.saveSubmission(
                templateId, inputMethod, allParams, allFiles);
            return ResponseEntity.ok(Map.of("submissionId", submissionId));
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Submission failed: " + e.getMessage());
        }
    }

    @GetMapping("/my-submissions")
    public ResponseEntity<List<SubmissionResponseDTO>> getMySubmissions() {
        return ResponseEntity.ok(formSubmissionService.getMySubmissions());
    }
}