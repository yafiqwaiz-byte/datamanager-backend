package dev.waiz.datamanager.service;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.time.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;


import dev.waiz.datamanager.dto.TemplateRequestDTO;
import dev.waiz.datamanager.dto.FormFieldDTO;
import dev.waiz.datamanager.dto.FormTemplateDTO;
import dev.waiz.datamanager.model.formfield;
import dev.waiz.datamanager.model.formtemplate;
import dev.waiz.datamanager.model.staff;
import dev.waiz.datamanager.repository.FormAnswerRepository;
import dev.waiz.datamanager.repository.FormFieldRepository;
import dev.waiz.datamanager.repository.formtemplaterepository;
import dev.waiz.datamanager.repository.staffrepository;
import jakarta.transaction.Transactional;

@Service
@Transactional
public class FormTemplateService {


    @Autowired
    private staffrepository staffRepository;

    @Autowired
    private formtemplaterepository formtemplaterepository;

    @Autowired
    private FormFieldRepository formfieldrepository;

    @Autowired
    private FormAnswerRepository formanswerrepository;



    public List<FormTemplateDTO> getActiveTemplates(){
        return formtemplaterepository.findByIsActiveTrue()
        .stream()
        .map(this::toDTO)
        .collect(Collectors.toList());
    }

    public List<FormTemplateDTO> getAllTemplatesForStaff(){

        String username = SecurityContextHolder.getContext()
        .getAuthentication()
        .getName();

        staff currentstaff = staffRepository.findByAccount_Username(username)
            .orElseThrow(() -> new RuntimeException("Staff not found for user: " + username));

        return formtemplaterepository.findByStaff(currentstaff).stream()
        .map(this::toDTO)
        .collect(Collectors.toList());  
    }

    public FormTemplateDTO getTemplateById(UUID id) {
        formtemplate template = formtemplaterepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Template not found"));
        return toDTO(template);
    }

    public FormTemplateDTO createTemplate(TemplateRequestDTO request) {

        String username = SecurityContextHolder.getContext()
        .getAuthentication()
        .getName();

        staff currentstaff = staffRepository.findByAccount_Username(username)
            .orElseThrow(() -> new RuntimeException("Staff not found for user: " + username));

        
        formtemplate template = new formtemplate();
        template.setTemplateName(request.getTemplateName());
        template.setDescription(request.getDescription());
        template.setIsActive(request.getIsActive()!=null ? request.getIsActive() : Boolean.TRUE);
        template.setCreatedAt(LocalDateTime.now());
        template.setStaff(currentstaff);

        formtemplate savedTemplate = formtemplaterepository.save(template);

        List<formfield> fields = request.getFields().stream()
            .map(dto -> {
                formfield f = new formfield();
                f.setFieldLabel(dto.getFieldLabel());
                f.setFieldType(dto.getFieldType());
                f.setIsRequired(dto.getIsRequired());
                f.setFieldOrder(dto.getFieldOrder());
                f.setPlaceholder(dto.getPlaceholder());
                f.setTemplate(savedTemplate);
                return f;
            })
            .collect(Collectors.toList());

        formfieldrepository.saveAll(fields);

         // Build DTO directly from repository — bypasses Hibernate cache
            FormTemplateDTO dto = new FormTemplateDTO();
            dto.setTemplateId(savedTemplate.getTemplateId());
            dto.setTemplateName(savedTemplate.getTemplateName());
            dto.setDescription(savedTemplate.getDescription());
            dto.setFields(
            formfieldrepository.findByTemplate_TemplateId(savedTemplate.getTemplateId())
            .stream()
            .sorted(Comparator.comparing(formfield::getFieldOrder))
            .map(this::fieldToDTO)
            .collect(Collectors.toList())
    );
    return dto;
    }

    public FormTemplateDTO updateTemplate(UUID id,TemplateRequestDTO request) {
        formtemplate template = formtemplaterepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Template not found"));
        template.setTemplateName(request.getTemplateName());
        template.setDescription(request.getDescription());
        if (request.getIsActive() != null) {
            template.setIsActive(request.getIsActive());
        }
        formtemplaterepository.save(template);

        // Delete answers first — they reference form_field via FK
        List<formfield> existingFields = formfieldrepository.findByTemplate_TemplateId(id);
        existingFields.forEach(field -> 
            formanswerrepository.deleteByField_FieldId(field.getFieldId())
        );

        formfieldrepository.deleteByTemplate_TemplateId(id);
        formfieldrepository.flush();

        if(request.getFields()!=null){
            List<formfield> fields = request.getFields().stream()
                .map(dto -> {
                formfield f = new formfield();
                f.setFieldLabel(dto.getFieldLabel());
                f.setFieldType(dto.getFieldType());
                f.setIsRequired(dto.getIsRequired());
                f.setFieldOrder(dto.getFieldOrder());
                f.setPlaceholder(dto.getPlaceholder());
                f.setTemplate(template);
                return f;
            })
            .collect(Collectors.toList());

            formfieldrepository.saveAll(fields);
        }
         // Build DTO directly from repository — bypasses Hibernate cache
            FormTemplateDTO dto = new FormTemplateDTO();
            dto.setTemplateId(template.getTemplateId());
            dto.setTemplateName(template.getTemplateName());
            dto.setDescription(template.getDescription());
            dto.setIsActive(template.getIsActive());
            dto.setFields(
            formfieldrepository.findByTemplate_TemplateId(id)
            .stream()
            .sorted(Comparator.comparing(formfield::getFieldOrder))
            .map(this::fieldToDTO)
            .collect(Collectors.toList())
    );
    return dto;
    }

    public void deleteTemplate(UUID id) {
        if (!formtemplaterepository.existsById(id)) {
            throw new RuntimeException("Template not found"+id);
        }
            // Delete answers first
        List<formfield> fields = formfieldrepository.findByTemplate_TemplateId(id);
        fields.forEach(field -> 
            formanswerrepository.deleteByField_FieldId(field.getFieldId())
        );
        formfieldrepository.deleteByTemplate_TemplateId(id);
        formtemplaterepository.deleteById(id);
    }


    public void toggleTemplate(UUID id){
        formtemplate t=formtemplaterepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Template not found"+id));
        t.setIsActive(!t.getIsActive());
        formtemplaterepository.save(t);
    }

    private FormTemplateDTO toDTO(formtemplate t) {
        FormTemplateDTO dto = new FormTemplateDTO();
        dto.setTemplateId(t.getTemplateId());
        dto.setTemplateName(t.getTemplateName());
        dto.setDescription(t.getDescription());
        dto.setIsActive(t.getIsActive());
        dto.setFields((t.getFields() != null ? t.getFields():List.<formfield>of())
            .stream()
            .sorted(Comparator.comparing(formfield::getFieldOrder))
            .map(this::fieldToDTO)
            .collect(Collectors.toList())
    );
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
