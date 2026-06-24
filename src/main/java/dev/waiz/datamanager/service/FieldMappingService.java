package dev.waiz.datamanager.service;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.regex.*;
import java.util.stream.Collectors;

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
    private final GeminiService geminiService;
    private final ObjectMapper objectMapper;

    public fieldmapping autoMap(UUID ocrID, UUID templateId) throws Exception {

        ocrresult ocr = ocrResultRepository.findById(ocrID)
            .orElseThrow(() -> new RuntimeException("OCR result not found: " + ocrID));

        lettertemplate template = letterTemplateRepository.findById(templateId)
            .orElseThrow(() -> new RuntimeException("Template not found: " + templateId));

        List<String> placeholders = objectMapper.readValue(
            template.getPlaceholderData(),
            new TypeReference<List<String>>() {});

        Map<String, String> ocrKeyValues = extractKeyValues(ocr.getExtractedText());

        log.info("=== OCR Extracted Key-Values ===");
        ocrKeyValues.forEach((k, v) -> log.info("  '{}' -> '{}'", k, v));
        log.info("=== Placeholders to match ===");
        placeholders.forEach(p -> log.info("  '{}'", p));

        // ── Pass 1: existing string-matching logic ────────────────────────────
        Map<String, String> mappedFields = new LinkedHashMap<>();

        for (String placeholder : placeholders) {
            String key = placeholder
                .replace("[", "")
                .replace("]", "")
                .replace("_", " ")
                .toLowerCase();

            String matched = findBestMatch(key, ocrKeyValues);
            mappedFields.put(placeholder, matched != null ? matched : "");
            log.info("Pass 1 mapped: {} -> {}", placeholder, matched);
        }

        // ── Pass 2: Gemini fallback for unresolved placeholders ───────────────
        // Collect placeholders that Pass 1 couldn't fill
        List<String> unmapped = mappedFields.entrySet().stream()
            .filter(e -> e.getValue().isBlank())
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());

        if (!unmapped.isEmpty()) {
            log.info("Pass 1 left {}/{} placeholders empty — calling Gemini for: {}",
                unmapped.size(), placeholders.size(), unmapped);

            // Only pass already-resolved fields as context so Gemini
            // doesn't re-guess what string matching already got right
            Map<String, String> partialResults = mappedFields.entrySet().stream()
                .filter(e -> !e.getValue().isBlank())
                .collect(Collectors.toMap(
                    Map.Entry::getKey,
                    Map.Entry::getValue,
                    (a, b) -> a,
                    LinkedHashMap::new
                ));

            try {
                Map<String, String> geminiResults = geminiService.mapOcrToPlaceholders(
                    ocr.getExtractedText(), unmapped, partialResults);

                // Merge Gemini results — only fill still-empty slots,
                // never overwrite what string matching already resolved
                geminiResults.forEach((ph, val) -> {
                    if (mappedFields.containsKey(ph) && mappedFields.get(ph).isBlank()
                            && val != null && !val.isBlank()) {
                        mappedFields.put(ph, val);
                        log.info("Pass 2 (Gemini) filled: {} -> {}", ph, val);
                    }
                });

            } catch (Exception e) {
                // Gemini failure is non-fatal — Pass 1 results are still saved.
                // The staff can manually fill the blanks in the confirmation step.
                log.warn("Gemini pass failed (non-fatal, Pass 1 results preserved): {}",
                    e.getMessage());
            }
        } else {
            log.info("All {} placeholders resolved in Pass 1 — skipping Gemini",
                placeholders.size());
        }

        // ── Final summary ──────────────────────────────────────────────────────
        long resolved = mappedFields.values().stream().filter(v -> !v.isBlank()).count();
        log.info("=== Mapping complete: {}/{} placeholders resolved ===",
            resolved, placeholders.size());

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
            Map<String, String> confirmedFields) throws Exception {
        fieldmapping mapping = fieldMappingRepository.findById(mappingId)
            .orElseThrow(() -> new RuntimeException("Mapping not found: " + mappingId));

        mapping.setMappedFields(objectMapper.writeValueAsString(confirmedFields));
        mapping.setStatus("confirmed");
        return fieldMappingRepository.save(mapping);
    }

    public List<fieldmapping> getMappingByOcr(UUID ocrId) {
        return fieldMappingRepository.findByOcr_OcrId(ocrId);
    }

    // ── Key-value extractor (unchanged) ───────────────────────────────────────
    public Map<String, String> extractKeyValues(String ocrText) {
        Map<String, String> keyValues = new LinkedHashMap<>();
        if (ocrText == null || ocrText.isEmpty()) {
            return keyValues;
        }
        String[] lines = ocrText.split("\\n");
        Pattern kvPattern = Pattern.compile("^(.+?)\\s*[:;|]+\\s*(.+)$");

        String lastKey = null;
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
                    lastKey = key;
                }
            } else {
                if (lastKey != null) {
                    String existing = keyValues.get(lastKey);
                    keyValues.put(lastKey, existing + " " + line);
                } else {
                    bodyText.append(line).append(" ");
                }
            }
        }
        if (bodyText.length() > 0) {
            keyValues.put("body text", bodyText.toString().trim());
        }
        return keyValues;
    }

    // ── Best-match finder (unchanged) ─────────────────────────────────────────
    private String findBestMatch(String placeholderKey,
                                  Map<String, String> ocrKeyValues) {
        if (ocrKeyValues.containsKey(placeholderKey)) {
            return ocrKeyValues.get(placeholderKey);
        }

        String phKey = placeholderKey.replaceAll("[^a-z0-9\\s]", "").trim();

        String bestMatchValue = null;
        double bestScore = 0.5;

        for (Map.Entry<String, String> entry : ocrKeyValues.entrySet()) {
            String ocrKey = entry.getKey().replaceAll("[^a-z0-9\\s]", "").trim();

            if (ocrKey.equals(phKey) || ocrKey.contains(phKey) || phKey.contains(ocrKey)) {
                return entry.getValue();
            }

            double cosine      = cosineSimilarity(phKey, ocrKey);
            double levenshtein = levenshteinSimilarity(phKey, ocrKey);
            double combined    = (cosine * 0.7) + (levenshtein * 0.3);

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

    // ── Cosine similarity (unchanged) ─────────────────────────────────────────
    private double cosineSimilarity(String s1, String s2) {
        Map<String, Integer> vec1 = wordVector(s1);
        Map<String, Integer> vec2 = wordVector(s2);

        double dotProduct = 0.0;
        for (Map.Entry<String, Integer> entry : vec1.entrySet()) {
            if (vec2.containsKey(entry.getKey())) {
                dotProduct += entry.getValue() * vec2.get(entry.getKey());
            }
        }

        double mag1 = Math.sqrt(vec1.values().stream().mapToDouble(v -> v * v).sum());
        double mag2 = Math.sqrt(vec2.values().stream().mapToDouble(v -> v * v).sum());

        if (mag1 == 0 || mag2 == 0) return 0.0;
        return dotProduct / (mag1 * mag2);
    }

    @SuppressWarnings("null")
    private Map<String, Integer> wordVector(String text) {
        Map<String, Integer> vector = new HashMap<>();
        for (String word : text.split("\\s+")) {
            if (!word.isEmpty()) {
                vector.merge(word, 1, Integer::sum);
            }
        }
        return vector;
    }

    // ── Levenshtein similarity (unchanged) ────────────────────────────────────
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