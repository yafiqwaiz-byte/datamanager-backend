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

        log.info("=== OCR Extracted Key-Values ===");
        ocrKeyValues.forEach((k, v) -> log.info("  '{}' -> '{}'", k, v));
        log.info("=== Placeholders to match ===");
        placeholders.forEach(p -> log.info("  '{}'", p));

        Map<String,String> mappedFields = new LinkedHashMap<>();

        for(String placeholder : placeholders) {
            String key = placeholder
                            .replace("[", "")
                            .replace("]", "")
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
        mapping.setStatus("pending");
        mapping.setCreatedAt(OffsetDateTime.now());

        return fieldMappingRepository.save(mapping);
    }

   
    public fieldmapping getMappingById(UUID mappingId) {
    return fieldMappingRepository.findById(mappingId)
        .orElseThrow(() -> new RuntimeException("Mapping not found: " + mappingId));
}

    public fieldmapping confirmMapping(UUID mappingId,
    Map<String,String> confirmedFields) throws Exception {
        fieldmapping mapping = fieldMappingRepository.findById(mappingId)
        .orElseThrow(() -> new RuntimeException("Mapping not found:" + mappingId));

        mapping.setMappedFields(objectMapper.writeValueAsString(confirmedFields));
        mapping.setStatus("confirmed");
        return fieldMappingRepository.save(mapping);
    }

    public List<fieldmapping> getMappingByOcr(UUID ocrId){
        return fieldMappingRepository.findByOcr_OcrId(ocrId);
    }

    public Map<String,String> extractKeyValues(String ocrText) {Map<String, String> keyValues = new LinkedHashMap<>();
    if (ocrText == null || ocrText.isEmpty()) {
        return keyValues;
    }
    String[] lines = ocrText.split("\\n");
    Pattern kvPattern = Pattern.compile("^(.+?)\\s*[:;|]+\\s*(.+)$");

    String lastkey = null;
    StringBuilder bodyText = new StringBuilder();

    for (String line : lines) {
        line = line.trim();
        if (line.isEmpty()) continue;
        
        Matcher m = kvPattern.matcher(line);
        if (m.matches()) {
            String key = m.group(1).trim().toLowerCase()
                        .replaceAll("[^a-z0-9\\s]", "").trim();
            String value = m.group(2).trim()
                        .replaceAll("^[:|\\s]+", "");

            if (!key.isEmpty() && !value.isEmpty()) {
                keyValues.put(key, value);
                lastkey = key;
            }
        } else {
            if(lastkey != null) {
                String existing = keyValues.get(lastkey);
                keyValues.put(lastkey, existing + " " + line);
            } else {
            // ✅ No separator — collect as body text
            bodyText.append(line).append(" ");
            }
        }
    }
    // ✅ Add body text as a separate key
    if (bodyText.length() > 0) {
        keyValues.put("body text", bodyText.toString().trim());
    }
    return keyValues;
}

    private String findBestMatch(String placeholderKey,
                                 Map<String,String> ocrKeyValues) {// 1. Exact match
    if (ocrKeyValues.containsKey(placeholderKey)) {
        return ocrKeyValues.get(placeholderKey);
    }

    String phKey = placeholderKey.replaceAll("[^a-z0-9\\s]", "").trim();

    String bestMatchValue = null;
    double bestScore = 0.5; // minimum threshold

    for (Map.Entry<String, String> entry : ocrKeyValues.entrySet()) {
        String ocrKey = entry.getKey().replaceAll("[^a-z0-9\\s]", "").trim();

        // 2. Contains match
        if (ocrKey.equals(phKey) || ocrKey.contains(phKey) || phKey.contains(ocrKey)) {
            return entry.getValue();
        }

        // 3. Combined score — cosine + levenshtein
        double cosine = cosineSimilarity(phKey, ocrKey);
        double levenshtein = levenshteinSimilarity(phKey, ocrKey);

        // ✅ Weighted average — cosine counts more
        double combined = (cosine * 0.7) + (levenshtein * 0.3);

        log.info("  '{}' vs '{}' → cosine={}, levenshtein={}, combined={}",
                 phKey, ocrKey, 
                 String.format("%.2f", cosine),
                 String.format("%.2f", levenshtein),
                 String.format("%.2f", combined));

        if (combined > bestScore) {
            bestScore = combined;
            bestMatchValue = entry.getValue();
        }
    }

    return bestMatchValue;
}
    
    // ── Cosine Similarity ─────────────────────────────────────────────
private double cosineSimilarity(String s1, String s2) {
    Map<String, Integer> vec1 = wordVector(s1);
    Map<String, Integer> vec2 = wordVector(s2);

    // Dot product
    double dotProduct = 0.0;
    for (Map.Entry<String, Integer> entry : vec1.entrySet()) {
        if (vec2.containsKey(entry.getKey())) {
            dotProduct += entry.getValue() * vec2.get(entry.getKey());
        }
    }

    // Magnitudes
    double mag1 = Math.sqrt(vec1.values().stream()
                  .mapToDouble(v -> v * v).sum());
    double mag2 = Math.sqrt(vec2.values().stream()
                  .mapToDouble(v -> v * v).sum());

    if (mag1 == 0 || mag2 == 0) return 0.0;
    return dotProduct / (mag1 * mag2);
}

private Map<String, Integer> wordVector(String text) {
    Map<String, Integer> vector = new HashMap<>();
    for (String word : text.split("\\s+")) {
        if (!word.isEmpty()) {
            vector.merge(word, 1, Integer::sum);
        }
    }
    return vector;
}

// ── Levenshtein Similarity ────────────────────────────────────────
private double levenshteinSimilarity(String s1, String s2) {
    if (s1.isEmpty() && s2.isEmpty()) return 1.0;
    if (s1.isEmpty() || s2.isEmpty()) return 0.0;
    int maxLen = Math.max(s1.length(), s2.length());
    return 1.0 - ((double) levenshteinDistance(s1, s2) / maxLen);
}

private int levenshteinDistance(String s1, String s2) {
    int[] dp = new int[s2.length() + 1];
    for (int i = 0; i <= s2.length(); i++) dp[i] = i;
    for (int i = 1; i <= s1.length(); i++) {
        int prev = dp[0];
        dp[0] = i;
        for (int j = 1; j <= s2.length(); j++) {
            int temp = dp[j];
            if (s1.charAt(i - 1) == s2.charAt(j - 1)) {
                dp[j] = prev;
            } else {
                dp[j] = 1 + Math.min(prev, Math.min(dp[j], dp[j - 1]));
            }
            prev = temp;
        }
    }
    return dp[s2.length()];
}

}
