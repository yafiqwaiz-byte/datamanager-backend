package dev.waiz.datamanager.service;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.regex.*;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.waiz.datamanager.model.fieldmapping;
import dev.waiz.datamanager.model.lettertemplate;
import dev.waiz.datamanager.model.ocrresult;
import dev.waiz.datamanager.repository.FieldMappingRepository;
import dev.waiz.datamanager.repository.LetterTemplateRepository;
import dev.waiz.datamanager.repository.OcrResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class FieldMappingService {

    private final FieldMappingRepository fieldMappingRepository;
    private final LetterTemplateRepository letterTemplateRepository;
    private final OcrResultRepository ocrResultRepository;

    private final ObjectMapper objectMapper;

    public fieldmapping autoMap(UUID ocrID,UUID templateId) throws Exception {

        ocrresult ocr = ocrResultRepository.findById(ocrID)
        .orElseThrow(() -> new RuntimeException("OCR result not found:" + ocrID));

        lettertemplate template = letterTemplateRepository.findById(templateId)
        .orElseThrow(() -> new RuntimeException("Template not found:" + templateId));

        List<String> placeholders = objectMapper.readValue(
            template.getPlaceholderData(),
             new TypeReference<List<String>>()  {});

        Map<String,String> ocrKeyValues = extractKeyValues(ocr.getExtractedText());

        Map<String,String> mappedFields = new LinkedHashMap<>();

        for(String placeholder : placeholders) {
            String key = placeholder
                            .replace("{{", "")
                            .replace("}}", "")
                            .replace("_", " ")
                            .toLowerCase();

            String matched = findBestMatch(key, ocrKeyValues);
            mappedFields.put(placeholder, matched != null ? matched : "");
            log.info("Mapped: {} -> {}",placeholder,matched);
        }

        fieldmapping mapping = new fieldmapping();
        mapping.setOcr(ocr);
        mapping.setLetterTemplate(template);
        mapping.setMappedFields(objectMapper.writeValueAsString(mappedFields));
        mapping.setStatus("PENDING");
        mapping.setCreatedAt(OffsetDateTime.now());

        return fieldMappingRepository.save(mapping);



        
    }

    public fieldmapping confirmMapping(UUID mappingId,
    Map<String,String> confirmedFields) throws Exception {
        fieldmapping mapping = fieldMappingRepository.findById(mappingId)
        .orElseThrow(() -> new RuntimeException("Mapping not found:" + mappingId));

        mapping.setMappedFields(objectMapper.writeValueAsString(confirmedFields));
        mapping.setStatus("CONFIRMED");
        return fieldMappingRepository.save(mapping);
    }

    public List<fieldmapping> getMappingByOcr(UUID ocrId){
        return fieldMappingRepository.findByOcr_OcrId(ocrId);
    }

    public Map<String,String> extractKeyValues(String ocrText) {
        Map<String, String> keyValues = new LinkedHashMap<>();
        if (ocrText == null || ocrText.isEmpty()) {
            return keyValues;
        }
        String[] lines = ocrText.split("\\n");
        Pattern kvPattern = Pattern.compile("^(.+?)[:|-]\\s*(.+)$");

        for (String line : lines) {
            Matcher m = kvPattern.matcher(line.trim());
            if(m.matches()) {
                String key = m.group(1).trim().toLowerCase();
                String value = m.group(2).trim();
                keyValues.put(key, value);
            }
        }
        return keyValues;
    
    }

    private String findBestMatch(String placeholderKey,
                                 Map<String,String> ocrKeyValues) {
        
        if (ocrKeyValues.containsKey(placeholderKey)) {
            return ocrKeyValues.get(placeholderKey);
        }
        
        for (Map.Entry<String,String> entry : ocrKeyValues.entrySet()) {

            if(entry.getKey().contains(placeholderKey) || placeholderKey.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
        
    }
    

}
