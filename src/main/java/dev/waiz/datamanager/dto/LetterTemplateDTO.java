package dev.waiz.datamanager.dto;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LetterTemplateDTO {
    private UUID letterTemplateId;
    private String templateName;
}
