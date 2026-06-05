package dev.waiz.datamanager.controller;

import org.springframework.core.io.*;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import org.springframework.http.HttpHeaders;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import dev.waiz.datamanager.dto.LetterTemplateDTO;
import dev.waiz.datamanager.model.fieldmapping;
import dev.waiz.datamanager.model.generatedletter;
import dev.waiz.datamanager.model.lettertemplate;
import dev.waiz.datamanager.service.FieldMappingService;
import dev.waiz.datamanager.service.LetterGeneratorService;
import dev.waiz.datamanager.service.TemplateUploadService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/letters")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:3000")
public class LetterController {

    private final TemplateUploadService templateUploadService;
    private final FieldMappingService fieldMappingService;
    private final LetterGeneratorService letterGeneratorService;

    @PostMapping("/templates/upload")
    public ResponseEntity<?> uploadTemplate(@RequestParam String templateName, @RequestParam MultipartFile file) {
        try {
            lettertemplate template = templateUploadService.uploadTemplate(templateName, file);
            return ResponseEntity.ok(Map.of(
                "letterTemplateId", template.getLetterTemplateId(),
                "templateName", template.getTemplateName(),
                "filePath", template.getFilePath()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Template upload failed: " + e.getMessage());
        }
    }

    @GetMapping("/templates/preview/{templateId}")
    public ResponseEntity<?> previewTemplate(@PathVariable UUID templateId) {
        try {
            lettertemplate template = templateUploadService.getTemplateById(templateId);
            String html = templateUploadService.convertToHtml(template.getFilePath());
            return ResponseEntity.ok(Map.of("html", html));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Preview failed: " + e.getMessage());
        }
    }

    @PostMapping("/templates/placeholders/{templateId}")
    public ResponseEntity<?> savePlaceholders(
        @PathVariable UUID templateId,
        @RequestBody List<Map<String,String>> placeholderMappings) {
        try {
            lettertemplate template = templateUploadService.savePlaceholders(templateId, placeholderMappings);
            return ResponseEntity.ok(Map.of(
                "letterTemplateId", template.getLetterTemplateId(),
                "templateName", template.getTemplateName(),
                "placeholders", template.getPlaceholderData()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Failed to save placeholders: " + e.getMessage());
        }
    }

    @GetMapping("/templates/all")
    public ResponseEntity<List<LetterTemplateDTO>> getAllTemplates() {
        return ResponseEntity.ok(templateUploadService.getAllTemplates());
    }

    @GetMapping("/templates/staff/{staffId}")
    public ResponseEntity<?> getTemplatesByStaffId(@PathVariable UUID staffId) {
        return ResponseEntity.ok(templateUploadService.getTemplatesByStaff(staffId));
    }

    @GetMapping("/mapping/{mappingId}")
    public ResponseEntity<?> getMappingById(@PathVariable UUID mappingId) {
        try {
            fieldmapping mapping = fieldMappingService.getMappingById(mappingId);
            return ResponseEntity.ok(Map.of(
                "mappingId", mapping.getMappingId(),
                "mappedFields", mapping.getMappedFields(),
                "status", mapping.getStatus()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Mapping not found: " + e.getMessage());
        }
    }

    @PostMapping("/mapping/auto")
    public ResponseEntity<?> autoMap(@RequestParam UUID ocrId, @RequestParam UUID templateId) {
        try {
            fieldmapping map = fieldMappingService.autoMap(ocrId, templateId);
            return ResponseEntity.ok(Map.of(
                "mappingId", map.getMappingId(),
                "mappedFields", map.getMappedFields(),
                "status", map.getStatus()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Auto-mapping failed: " + e.getMessage());
        }
    }

    @PutMapping("/mapping/confirm/{mappingId}")
    public ResponseEntity<?> confirmMapping(
        @PathVariable UUID mappingId,
        @RequestBody Map<String,String> correctedField) {
        try {
            fieldmapping map = fieldMappingService.confirmMapping(mappingId, correctedField);
            return ResponseEntity.ok(Map.of(
                "mappingId", map.getMappingId(),
                "status", map.getStatus()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Mapping confirmation failed: " + e.getMessage());
        }
    }

    @PostMapping("/generate/{mappingId}")
    public ResponseEntity<?> generateLetter(@PathVariable UUID mappingId) {
        try {
            generatedletter letter = letterGeneratorService.generateLetter(mappingId);
            return ResponseEntity.ok(Map.of(
                "letterId", letter.getLetterId(),
                "docxPath", letter.getDocxPath(),
                "pdfPath", letter.getPdfPath(),
                "generatedAt", letter.getGeneratedAt()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Letter generation failed: " + e.getMessage());
        }
    }

    @GetMapping("/generated/{mappingId}")
    public ResponseEntity<?> getGeneratedLetters(@PathVariable UUID mappingId) {
        return ResponseEntity.ok(
            letterGeneratorService.getLettersByMapping(mappingId)
                .stream()
                .map(letter -> Map.of(
                    "letterId", letter.getLetterId(),
                    "docxPath", letter.getDocxPath(),
                    "pdfPath", letter.getPdfPath(),
                    "generatedAt", letter.getGeneratedAt()
                ))
                .toList()
        );
    }

    // ✅ Fixed — find by letterId directly
    @GetMapping("/download/docx/{letterId}")
    public ResponseEntity<Resource> downloadDocx(@PathVariable UUID letterId) throws Exception {
        generatedletter letter = letterGeneratorService.getLetterById(letterId);
        Resource resource = new FileSystemResource(letter.getDocxPath());
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=letter.docx")
            .body(resource);
    }

    @GetMapping("/download/pdf/{letterId}")
    public ResponseEntity<Resource> downloadPdf(@PathVariable UUID letterId) throws Exception {
        generatedletter letter = letterGeneratorService.getLetterById(letterId);
        Resource resource = new FileSystemResource(letter.getPdfPath());
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=letter.pdf")
            .body(resource);
    }
}