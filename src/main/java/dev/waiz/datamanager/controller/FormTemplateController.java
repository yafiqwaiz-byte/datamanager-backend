package dev.waiz.datamanager.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.waiz.datamanager.dto.FormTemplateDTO;
import dev.waiz.datamanager.service.FormTemplateService;

@RestController
@RequestMapping("/api/form-templates")
public class FormTemplateController {

    private FormTemplateService formTemplateService;

    public ResponseEntity<List<FormTemplateDTO>> getActiveTemplates() {
        List<FormTemplateDTO> templates = formTemplateService.getActiveTemplates();
        return ResponseEntity.ok(templates);
    }

    public ResponseEntity<FormTemplateDTO> getTemplateById(@PathVariable UUID id) {
        FormTemplateDTO template = formTemplateService.getTemplateById(id);
        return ResponseEntity.ok(template);
    }

}
