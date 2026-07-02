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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import dev.waiz.datamanager.dto.LetterTemplateDTO;
import dev.waiz.datamanager.model.fieldmapping;
import dev.waiz.datamanager.model.generatedletter;
import dev.waiz.datamanager.model.lettertemplate;
import dev.waiz.datamanager.model.ocrresult;
import dev.waiz.datamanager.service.FieldMappingService;
import dev.waiz.datamanager.service.LetterGeneratorService;
import dev.waiz.datamanager.service.TemplateUploadService;
import dev.waiz.datamanager.repository.LetterTemplateRepository;
import dev.waiz.datamanager.repository.OcrResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/letters")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:3000")
@Slf4j
public class LetterController {

    private final TemplateUploadService templateUploadService;
    private final FieldMappingService fieldMappingService;
    private final LetterGeneratorService letterGeneratorService;
    private final OcrResultRepository ocrResultRepository;
    private final LetterTemplateRepository letterTemplateRepository;

    // ══════════════════════════════════════════════════════════════
    //  SHARED — Templates (USER + STAFF)
    // ══════════════════════════════════════════════════════════════

    /**
     * USER + STAFF: Get all available letter templates for dropdown
     */
    @GetMapping("/templates/all")
    public ResponseEntity<List<LetterTemplateDTO>> getAllTemplates() {
        return ResponseEntity.ok(templateUploadService.getAllTemplates());
    }

    // ══════════════════════════════════════════════════════════════
    //  USER — OCR submission & status tracking
    // ══════════════════════════════════════════════════════════════

    /**
     * USER: Submit OCR result + chosen template for staff to process.
     * No auto-mapping here — staff handles it from the queue.
     */
    @PostMapping("/submit")
    public ResponseEntity<?> submitForProcessing(
            @RequestParam UUID ocrId,
            @RequestParam UUID templateId) {
        try {
            ocrresult ocr = ocrResultRepository.findById(ocrId)
                .orElseThrow(() -> new RuntimeException("OCR result not found: " + ocrId));

            lettertemplate template = letterTemplateRepository.findById(templateId)
                .orElseThrow(() -> new RuntimeException("Template not found: " + templateId));

            ocr.setStatus("pending_review");
            ocr.setSelectedTemplate(template);
            ocrResultRepository.save(ocr);

            log.info("User submitted OCR {} with template {} for staff review",
                ocrId, templateId);

            return ResponseEntity.ok(Map.of(
                "ocrId",      ocrId,
                "templateId", templateId,
                "status",     "pending_review",
                "message",    "Submitted successfully. Staff will process your request shortly."
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Submission failed: " + e.getMessage());
        }
    }

    /**
     * USER: Check status of their OCR submission.
     * Returns download links when status is "ready".
     */
    @GetMapping("/status/{ocrId}")
    public ResponseEntity<?> getLetterStatus(@PathVariable UUID ocrId) {
        try {
            ocrresult ocr = ocrResultRepository.findById(ocrId)
                .orElseThrow(() -> new RuntimeException("OCR result not found: " + ocrId));

            String status = ocr.getStatus() != null ? ocr.getStatus() : "uploaded";

            // If letter is ready — include download links
            if ("ready".equals(status)) {
                List<fieldmapping> mappings = fieldMappingService.getMappingByOcr(ocrId);

                if (!mappings.isEmpty()) {
                    fieldmapping latestMapping = mappings.get(mappings.size() - 1);
                    List<generatedletter> letters = letterGeneratorService
                        .getLettersByMapping(latestMapping.getMappingId());

                    if (!letters.isEmpty()) {
                        generatedletter letter = letters.get(letters.size() - 1);
                        return ResponseEntity.ok(Map.of(
                            "ocrId",        ocrId,
                            "status",       status,
                            "message",      "Your letter is ready to download!",
                            "letterId",     letter.getLetterId(),
                            "generatedAt",  letter.getGeneratedAt(),
                            "downloadPdf",  "/letters/download/pdf/"  + letter.getLetterId(),
                            "downloadDocx", "/letters/download/docx/" + letter.getLetterId()
                        ));
                    }
                }
            }

            String message = switch (status) {
                case "uploaded"       -> "OCR extraction complete. Waiting for submission.";
                case "pending_review" -> "Submitted. Staff will review shortly.";
                case "mapping"        -> "Staff is processing your document.";
                case "confirmed"      -> "Fields confirmed. Generating your letter...";
                case "ready"          -> "Your letter is ready to download!";
                default               -> "Processing...";
            };

            return ResponseEntity.ok(Map.of(
                "ocrId",   ocrId,
                "status",  status,
                "message", message
            ));

        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Status check failed: " + e.getMessage());
        }
    }

    /**
     * USER: Get all OCR submissions belonging to the currently
     * logged-in user, identified via their authenticated username
     * (from the security context) — no path param needed.
     * Returns newest-first.
     */
    @GetMapping("/status/my")
    public ResponseEntity<?> getMySubmissions() {
        try {
            String username = org.springframework.security.core.context
                .SecurityContextHolder.getContext()
                .getAuthentication()
                .getName();

            List<ocrresult> results =
                ocrResultRepository.findAllByUsernameWithUpload(username);

            List<Map<String, Object>> response = results.stream()
                .sorted((a, b) -> {
                    if (a.getProcessedAt() == null) return 1;
                    if (b.getProcessedAt() == null) return -1;
                    return b.getProcessedAt().compareTo(a.getProcessedAt());
                })
                .map(ocr -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("ocrId",        ocr.getOcrId());
                    item.put("status",       ocr.getStatus() != null
                                                ? ocr.getStatus() : "uploaded");
                    item.put("processedAt",  ocr.getProcessedAt());
                    item.put("templateName", ocr.getSelectedTemplate() != null
                                                ? ocr.getSelectedTemplate().getTemplateName()
                                                : null);
                    return item;
                })
                .toList();

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body("Failed to fetch submissions: " + e.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  STAFF — Template management
    // ══════════════════════════════════════════════════════════════

    @PostMapping("/templates/upload")
    public ResponseEntity<?> uploadTemplate(
            @RequestParam String templateName,
            @RequestParam MultipartFile file) {
        try {
            lettertemplate template =
                templateUploadService.uploadTemplate(templateName, file);
            return ResponseEntity.ok(Map.of(
                "letterTemplateId", template.getLetterTemplateId(),
                "templateName",     template.getTemplateName(),
                "filePath",         template.getFilePath()
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
            @RequestBody List<Map<String, String>> placeholderMappings) {
        try {
            lettertemplate template =
                templateUploadService.savePlaceholders(templateId, placeholderMappings);
            return ResponseEntity.ok(Map.of(
                "letterTemplateId", template.getLetterTemplateId(),
                "templateName",     template.getTemplateName(),
                "placeholders",     template.getPlaceholderData()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body("Failed to save placeholders: " + e.getMessage());
        }
    }

    @GetMapping("/templates/staff/{staffId}")
    public ResponseEntity<?> getTemplatesByStaffId(@PathVariable UUID staffId) {
        return ResponseEntity.ok(templateUploadService.getTemplatesByStaff(staffId));
    }

    // ══════════════════════════════════════════════════════════════
    //  STAFF — OCR submission queue
    // ══════════════════════════════════════════════════════════════

    /**
     * STAFF: Get all OCR submissions with status "pending_review"
     */
    @GetMapping("/queue/pending")
    public ResponseEntity<?> getPendingQueue() {
        try {
            // ✅ Use fetch-joined query to avoid N+1 on ocr.upload
            List<ocrresult> pending = ocrResultRepository.findByStatusWithUpload("pending_review");

            List<Map<String, Object>> response = pending.stream()
                .map(ocr -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("ocrId",        ocr.getOcrId());
                    item.put("status",       ocr.getStatus());
                    item.put("extractedText", ocr.getExtractedText());
                    item.put("processedAt",  ocr.getProcessedAt());
                    // ✅ Fix 3 — return ID and name, not the whole object
                    item.put("selectedTemplateId", ocr.getSelectedTemplate() != null
                        ? ocr.getSelectedTemplate().getLetterTemplateId() : null);
                    item.put("selectedTemplateName", ocr.getSelectedTemplate() != null
                        ? ocr.getSelectedTemplate().getTemplateName() : null);
                    item.put("fileName", ocr.getUpload() != null
                        ? ocr.getUpload().getFileName() : "unknown");
                    return item;
                })
                .toList();

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Failed to fetch queue: " + e.getMessage());
        }
    }

    /**
     * STAFF: Get all OCR submissions regardless of status
     */
    @GetMapping("/queue/all")
    public ResponseEntity<?> getAllQueue() {
        try {
            // ✅ Use fetch-joined query to avoid N+1 on ocr.upload
            List<ocrresult> all = ocrResultRepository.findAllByStatusNotNullWithUpload();

            List<Map<String, Object>> response = all.stream()
                .map(ocr -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("ocrId",       ocr.getOcrId());
                    item.put("status",      ocr.getStatus());
                    item.put("processedAt", ocr.getProcessedAt());
                    // ✅ Fix 3 — return ID and name, not the whole object
                    item.put("selectedTemplateId", ocr.getSelectedTemplate() != null
                        ? ocr.getSelectedTemplate().getLetterTemplateId() : null);
                    item.put("selectedTemplateName", ocr.getSelectedTemplate() != null
                        ? ocr.getSelectedTemplate().getTemplateName() : null);
                    item.put("fileName", ocr.getUpload() != null
                        ? ocr.getUpload().getFileName() : "unknown");
                    return item;
                })
                .toList();

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Failed to fetch queue: " + e.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  STAFF — Mapping (auto-map, review, confirm)
    // ══════════════════════════════════════════════════════════════

    /**
     * STAFF: Trigger auto-mapping for a pending OCR submission
     */
    @PostMapping("/mapping/auto")
    public ResponseEntity<?> autoMap(
            @RequestParam UUID ocrId,
            @RequestParam UUID templateId) {
        try {
            ocrresult ocr = ocrResultRepository.findById(ocrId)
                .orElseThrow(() -> new RuntimeException("OCR result not found: " + ocrId));
            ocr.setStatus("mapping");
            ocrResultRepository.save(ocr);

            fieldmapping map = fieldMappingService.autoMap(ocrId, templateId);

            return ResponseEntity.ok(Map.of(
                "mappingId",         map.getMappingId(),
                "mappedFields",      map.getMappedFields(),
                "validationSummary", map.getValidationSummary() != null
                                        ? map.getValidationSummary() : "{}",
                "status",            map.getStatus()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Auto-mapping failed: " + e.getMessage());
        }
    }

    /**
     * STAFF: Get mapping by OCR ID — used by StaffLetterReview
     * to load existing mapping when staff revisits a submission.
     * Returns 204 No Content if no mapping exists yet.
     */
    @GetMapping("/mapping/by-ocr/{ocrId}")
    public ResponseEntity<?> getMappingByOcrId(@PathVariable UUID ocrId) {
        try {
            List<fieldmapping> mappings = fieldMappingService.getMappingByOcr(ocrId);

            if (mappings.isEmpty()) {
                return ResponseEntity.noContent().build(); // 204 — no mapping yet
            }

            // Return the latest mapping
            fieldmapping latest = mappings.get(mappings.size() - 1);

            return ResponseEntity.ok(Map.of(
                "mappingId",         latest.getMappingId(),
                "mappedFields",      latest.getMappedFields(),
                "validationSummary", latest.getValidationSummary() != null
                                        ? latest.getValidationSummary() : "{}",
                "status",            latest.getStatus()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body("Failed to fetch mapping: " + e.getMessage());
        }
    }

    /**
     * STAFF: Get mapping details for review
     */
    @GetMapping("/mapping/{mappingId}")
    public ResponseEntity<?> getMappingById(@PathVariable UUID mappingId) {
        try {
            fieldmapping mapping = fieldMappingService.getMappingById(mappingId);
            return ResponseEntity.ok(Map.of(
                "mappingId",         mapping.getMappingId(),
                "mappedFields",      mapping.getMappedFields(),
                "validationSummary", mapping.getValidationSummary() != null
                                        ? mapping.getValidationSummary() : "{}",
                "status",            mapping.getStatus()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Mapping not found: " + e.getMessage());
        }
    }

    /**
     * STAFF: Confirm mapping after reviewing/fixing fields
     */
    @PutMapping("/mapping/confirm/{mappingId}")
    public ResponseEntity<?> confirmMapping(
            @PathVariable UUID mappingId,
            @RequestBody Map<String, String> correctedFields) {
        try {
            fieldmapping map = fieldMappingService.confirmMapping(mappingId, correctedFields);

            ocrresult ocr = map.getOcr();
            ocr.setStatus("confirmed");
            ocrResultRepository.save(ocr);

            return ResponseEntity.ok(Map.of(
                "mappingId", map.getMappingId(),
                "status",    map.getStatus()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body("Mapping confirmation failed: " + e.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  STAFF — Letter generation
    // ══════════════════════════════════════════════════════════════

    /**
     * STAFF: Generate letter after confirming mapping
     */
    @PostMapping("/generate/{mappingId}")
    public ResponseEntity<?> generateLetter(@PathVariable UUID mappingId) {
        try {
            generatedletter letter = letterGeneratorService.generateLetter(mappingId);

            ocrresult ocr = letter.getMapping().getOcr();
            ocr.setStatus("ready");
            ocrResultRepository.save(ocr);

            log.info("Letter generated — mappingId: {}, letterId: {}",
                mappingId, letter.getLetterId());

            return ResponseEntity.ok(Map.of(
                "letterId",     letter.getLetterId(),
                "generatedAt",  letter.getGeneratedAt(),
                "downloadPdf",  "/letters/download/pdf/"  + letter.getLetterId(),
                "downloadDocx", "/letters/download/docx/" + letter.getLetterId()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body("Letter generation failed: " + e.getMessage());
        }
    }

    @GetMapping("/generated/{mappingId}")
    public ResponseEntity<?> getGeneratedLetters(@PathVariable UUID mappingId) {
        return ResponseEntity.ok(
            letterGeneratorService.getLettersByMapping(mappingId)
                .stream()
                .map(letter -> Map.of(
                    "letterId",     letter.getLetterId(),
                    "generatedAt",  letter.getGeneratedAt(),
                    "downloadPdf",  "/letters/download/pdf/"  + letter.getLetterId(),
                    "downloadDocx", "/letters/download/docx/" + letter.getLetterId()
                ))
                .toList()
        );
    }

    // ══════════════════════════════════════════════════════════════
    //  SHARED — Download (USER + STAFF)
    // ══════════════════════════════════════════════════════════════

    @GetMapping("/download/pdf/{letterId}")
    public ResponseEntity<Resource> downloadPdf(@PathVariable UUID letterId) throws Exception {
        generatedletter letter = letterGeneratorService.getLetterById(letterId);
        Resource resource = new FileSystemResource(letter.getPdfPath());
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=letter.pdf")
            .body(resource);
    }

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
}