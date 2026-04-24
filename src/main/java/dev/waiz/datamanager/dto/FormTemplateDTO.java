package dev.waiz.datamanager.dto;

import java.util.List;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class FormTemplateDTO {

    private UUID templateId;
    private String templateName;
    private String description;
    private List<FormFieldDTO> fields;
}
