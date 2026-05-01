package dev.waiz.datamanager.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import dev.waiz.datamanager.dto.SubmissionResponseDTO;
import dev.waiz.datamanager.service.FormSubmissionService;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/forms")
public class FormSubmissionController {

    @Autowired
    private FormSubmissionService formSubmissionService;

    @PostMapping(value = "/submit", consumes = "multipart/form-data")
    public ResponseEntity<?> submit(
            @RequestParam("templateId") UUID templateId,
            @RequestParam("inputMethod") String inputMethod,
            HttpServletRequest request) {
        try {
            Map<String,String> allParams = new HashMap<>();
            request.getParameterMap().forEach((key,values) ->{
                if (values !=null && values.length >0){
                    allParams.put(key, values[0]);
                }
            });

            MultiValueMap<String, MultipartFile> allFiles = new LinkedMultiValueMap<>();
            if (request instanceof MultipartHttpServletRequest multipartrequest){
                multipartrequest.getMultiFileMap().forEach((key, files) ->{
                    if (key.startsWith("file_")){
                        allFiles.addAll(key, files);
                    }
                });
            }

            UUID submissionId = formSubmissionService.saveSubmission(templateId, inputMethod, allParams, allFiles);
            return ResponseEntity.ok(Map.of("submissionId",submissionId));
        } catch (Exception e){
            return ResponseEntity.status(500).body("Submission failed:" + e.getMessage());
        }
    }

    @GetMapping("/my-submissions")
    public ResponseEntity<List<SubmissionResponseDTO>> getMySubmissions() {
        return ResponseEntity.ok(formSubmissionService.getMySubmissions());
    }
}