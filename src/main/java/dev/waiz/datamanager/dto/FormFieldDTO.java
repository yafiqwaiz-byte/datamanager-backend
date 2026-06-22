package dev.waiz.datamanager.dto;

import java.util.List;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FormFieldDTO {

    private UUID fieldId;
    private String fieldLabel;
    private String fieldType;
    private Boolean isRequired;
    private Integer fieldOrder;
    private String placeholder;
    private List<String> imageLabels;
}
