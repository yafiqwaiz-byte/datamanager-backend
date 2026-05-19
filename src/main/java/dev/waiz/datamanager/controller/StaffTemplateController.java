package dev.waiz.datamanager.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
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

@RestController
@RequestMapping("/api/staff/templates")
public class StaffTemplateController {

    @Autowired
    private FormTemplateService formTemplateService;

    @Autowired
    private FormSubmissionService formSubmissionService;

    // ✅ From FormTemplateController — get all active templates
    @GetMapping
    public ResponseEntity<List<FormTemplateDTO>> getActiveTemplates() {
        return ResponseEntity.ok(formTemplateService.getActiveTemplates());
    }

    // ✅ From StaffTemplateController — get all templates for current staff
    @GetMapping("/all")
    public ResponseEntity<List<FormTemplateDTO>> getAllTemplates() {
        return ResponseEntity.ok(formTemplateService.getAllTemplates());
    }

    // ✅ From FormTemplateController — get template by id
    @GetMapping("/{id}")
    public ResponseEntity<FormTemplateDTO> getTemplateById(@PathVariable UUID id) {
        return ResponseEntity.ok(formTemplateService.getTemplateById(id));
    }

      // ✅ From StaffTemplateController — get all submissions across all staff templates
    @GetMapping("/submissions/all")
    public ResponseEntity<Page<SubmissionResponseDTO>> getAllSubmissions(@RequestParam(defaultValue = "0")int page,@RequestParam(defaultValue = "10")int size) {
        return ResponseEntity.ok(formSubmissionService.getAllSubmissions(page,size));
    }

    // ✅ From FormTemplateController — get submissions for a template
    @GetMapping("/{id}/submissions")
    public ResponseEntity<Page<SubmissionResponseDTO>> getSubmissions(@PathVariable UUID id,@RequestParam(defaultValue = "0" ) int page,@RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(formSubmissionService.getSubmissionsByTemplate(id,page,size));
    }


    // ✅ Create template
    @PostMapping
    public ResponseEntity<FormTemplateDTO> createTemplate(@RequestBody TemplateRequestDTO request) {
        return ResponseEntity.ok(formTemplateService.createTemplate(request));
    }

    // ✅ Update template
    @PutMapping("/{id}")
    public ResponseEntity<FormTemplateDTO> updateTemplate(@PathVariable UUID id, @RequestBody TemplateRequestDTO request) {
        return ResponseEntity.ok(formTemplateService.updateTemplate(id, request));
    }

    // ✅ Toggle active/inactive
    @PatchMapping("/{id}/toggle")
    public ResponseEntity<Void> toggleActive(@PathVariable UUID id) {
        formTemplateService.toggleActive(id);
        return ResponseEntity.noContent().build();
    }

    // ✅ Delete template
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTemplate(@PathVariable UUID id) {
        formTemplateService.deleteTemplate(id);
        return ResponseEntity.noContent().build();
    }
}