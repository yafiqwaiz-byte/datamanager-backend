package dev.waiz.datamanager.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.waiz.datamanager.dto.FormTemplateDTO;
import dev.waiz.datamanager.dto.SubmissionResponseDTO;
import dev.waiz.datamanager.dto.TemplateRequestDTO;
import dev.waiz.datamanager.service.FormSubmissionService;
import dev.waiz.datamanager.service.FormTemplateService;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/forms")
public class FormTemplateController {

    
    private final FormTemplateService formTemplateService;

    private final FormSubmissionService formSubmissionService;

    // USER:  GET /api/forms/templates?active=true
    // STAFF: GET /api/forms/templates  
    @GetMapping("/templates")
    public ResponseEntity<List<FormTemplateDTO>> getTemplates(
            @RequestParam(required = false) Boolean active) {
        if (Boolean.TRUE.equals(active)) {
            return ResponseEntity.ok(formTemplateService.getActiveTemplates());
        }
        return ResponseEntity.ok(formTemplateService.getAllTemplatesForStaff());
    }

    // GET /api/forms/templates/{id}
    @GetMapping("/templates/{id}")
    public ResponseEntity<FormTemplateDTO> getTemplateById(@PathVariable UUID id) {
        return ResponseEntity.ok(formTemplateService.getTemplateById(id));
    }

    // POST /api/forms/templates
    @PostMapping("/templates")
    public ResponseEntity<FormTemplateDTO> createTemplate(@RequestBody TemplateRequestDTO request) {
        return ResponseEntity.ok(formTemplateService.createTemplate(request));
    }

    // PUT /api/forms/templates/{id}
    @PutMapping("/templates/{id}")
    public ResponseEntity<FormTemplateDTO> updateTemplate(
            @PathVariable UUID id,
            @RequestBody TemplateRequestDTO request) {
        return ResponseEntity.ok(formTemplateService.updateTemplate(id, request));
    }

    // PATCH /api/forms/templates/{id}/toggle
    @PatchMapping("/templates/{id}/toggle")
    public ResponseEntity<?> toggleTemplate(@PathVariable UUID id) {
        formTemplateService.toggleTemplate(id);
        return ResponseEntity.ok().build();
    }

    // DELETE /api/forms/templates/{id}
    @DeleteMapping("/templates/{id}")
    public ResponseEntity<?> deleteTemplate(@PathVariable UUID id) {
        formTemplateService.deleteTemplate(id);
        return ResponseEntity.ok().build();
    }

    // GET /api/forms/templates/{id}/submissions
    @GetMapping("/templates/{id}/submissions")
    public ResponseEntity<Page<SubmissionResponseDTO>> getTemplateSubmissions(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(formSubmissionService.getSubmissionsByTemplate(id, page, size));
    }

    // GET /api/forms/submissions/all
    @GetMapping("/submissions/all")
    public ResponseEntity<Page<SubmissionResponseDTO>> getAllSubmissions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(formSubmissionService.getAllSubmissions(page, size));
    }
}