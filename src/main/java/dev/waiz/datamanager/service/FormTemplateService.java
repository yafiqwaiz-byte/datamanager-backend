package dev.waiz.datamanager.service;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dev.waiz.datamanager.dto.FormFieldDTO;
import dev.waiz.datamanager.dto.FormTemplateDTO;
import dev.waiz.datamanager.model.formfield;
import dev.waiz.datamanager.model.formtemplate;
import dev.waiz.datamanager.repository.formtemplaterepository;
import jakarta.transaction.Transactional;

@Service
@Transactional
public class FormTemplateService {


    @Autowired
    private formtemplaterepository formtemplaterepository;

    public List<FormTemplateDTO> getActiveTemplates(){
        return formtemplaterepository.findByIsActiveTrue().stream()
        .map(this::toDTO)
        .collect(Collectors.toList());  
    }

    public FormTemplateDTO getTemplateById(UUID id) {
        formtemplate template = formtemplaterepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Template not found"));
        return toDTO(template);
    }

    private FormTemplateDTO toDTO(formtemplate t) {
        FormTemplateDTO dto = new FormTemplateDTO();
        dto.setTemplateId(t.getTemplateId());
        dto.setTemplateName(t.getTemplateName());
        dto.setDescription(t.getDescription());
        dto.setFields(t.getFields().stream()
            .sorted(Comparator.comparing(formfield::getFieldOrder))
            .map(this::fieldToDTO)
            .collect(Collectors.toList()));
        return dto;
    }

    private FormFieldDTO fieldToDTO(formfield f) {
        FormFieldDTO dto = new FormFieldDTO();
        dto.setFieldId(f.getFieldId());
        dto.setFieldLabel(f.getFieldLabel());
        dto.setFieldType(f.getFieldType());
        dto.setIsRequired(f.getIsRequired());
        dto.setFieldOrder(f.getFieldOrder());
        dto.setPlaceholder(f.getPlaceholder());
        return dto;
    }

}
