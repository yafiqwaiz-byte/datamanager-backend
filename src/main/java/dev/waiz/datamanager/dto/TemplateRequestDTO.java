package dev.waiz.datamanager.dto;

import java.util.List;

import lombok.Data;

@Data
public class TemplateRequestDTO {

    private String templateName;
    private String description;
    private Boolean isActive;
    private List<FormFieldDTO> fields;
}
