package dev.waiz.datamanager.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import dev.waiz.datamanager.dto.FormTemplateDTO;

import dev.waiz.datamanager.service.FormTemplateService;



@RestController
@RequestMapping("/api/forms")
public class FormTemplateController {

    @Autowired
    private FormTemplateService formTemplateService;

    @GetMapping({"/templates","/staff/templates"})
    public ResponseEntity<List<FormTemplateDTO>> getActiveTemplates() {
        List<FormTemplateDTO> templates = formTemplateService.getActiveTemplates();
        return ResponseEntity.ok(templates);
    }

    @GetMapping("/templates/{id}") 
    public ResponseEntity<FormTemplateDTO> getTemplateById(@PathVariable UUID id) {
        FormTemplateDTO template = formTemplateService.getTemplateById(id);
        return ResponseEntity.ok(template);
    }

}
