package dev.waiz.datamanager.controller;

import java.util.List;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.waiz.datamanager.dto.FormTemplateDTO;
import dev.waiz.datamanager.service.FormTemplateService;

@RestController
@RequestMapping("/api/forms")
public class UserFormController {
    @Autowired
    private FormTemplateService formTemplateService;

    // GET /api/forms/templates — public active templates for users
    @GetMapping("/user-templates")
    public ResponseEntity<List<FormTemplateDTO>> getActiveTemplates() {
        return ResponseEntity.ok(formTemplateService.getActiveTemplates());
    }

}
