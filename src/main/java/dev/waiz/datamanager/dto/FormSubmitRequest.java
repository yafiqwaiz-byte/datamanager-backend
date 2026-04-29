package dev.waiz.datamanager.dto;

import java.util.Map;
import java.util.UUID;

import lombok.Data;

@Data
public class FormSubmitRequest {

    private UUID templateId;
    private String inputMethod;
    private Map<UUID,String> answers;
}
