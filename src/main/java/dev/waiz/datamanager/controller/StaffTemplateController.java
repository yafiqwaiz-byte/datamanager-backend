package dev.waiz.datamanager.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.waiz.datamanager.dto.FormTemplateDTO;
import dev.waiz.datamanager.dto.TemplateRequestDTO;
import dev.waiz.datamanager.service.FormTemplateService;

@RestController
@RequestMapping("/api/staff/templates")
public class StaffTemplateController {

    @Autowired
    private FormTemplateService formTemplateService;

    @GetMapping
    public ResponseEntity<List<FormTemplateDTO>> getAll(){
        return ResponseEntity.ok(formTemplateService.getAllTemplates());
    }

    @GetMapping("/{id}")
    public ResponseEntity<FormTemplateDTO> getById(@PathVariable UUID id){
        return ResponseEntity.ok(formTemplateService.getTemplateById(id));
    }

    @PostMapping
    public ResponseEntity<FormTemplateDTO> create(@RequestBody TemplateRequestDTO request){
        return ResponseEntity.ok(formTemplateService.createTemplate(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<FormTemplateDTO> update(@PathVariable UUID id, @RequestBody TemplateRequestDTO request){
        return ResponseEntity.ok(formTemplateService.updateTemplate(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id){
        formTemplateService.deleteTemplate(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/toggle")
    public ResponseEntity<Void> toggleActive(@PathVariable UUID id){
        formTemplateService.toggleActive(id);
        return ResponseEntity.noContent().build();
    }

}
