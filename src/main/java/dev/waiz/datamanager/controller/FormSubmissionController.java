package dev.waiz.datamanager.controller;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import dev.waiz.datamanager.dto.SubmissionResponseDTO;
import dev.waiz.datamanager.service.FormSubmissionService;

@RestController
@RequestMapping("/api/submissions")
public class FormSubmissionController {

    private FormSubmissionService formSubmissionService;

     public ResponseEntity<?> submitForm(
        @RequestParam("templateId") UUID templateId,
        @RequestParam("inputMethod") String inputMethod,
        @RequestParam Map<String, String> allParams,
        @RequestParam(required = false) Map<String, MultipartFile> allFiles
    ) {
        try {
            formSubmissionService.saveSubmission(templateId, inputMethod, allParams, allFiles);
            return ResponseEntity.ok("Form submitted successfully");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Submission failed: " + e.getMessage());
        }
    }

    @GetMapping("/my-submissions")
public ResponseEntity<List<SubmissionResponseDTO>> getMySubmissions() {
    return ResponseEntity.ok(formSubmissionService.getMySubmissions());
}
}
