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
import dev.waiz.datamanager.service.RegexValidationService.ValidationResult;
import dev.waiz.datamanager.service.BusinessValidationService.BusinessViolation;
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
    private final RegexValidationService regexValidationService;
    private final BusinessValidationService businessValidationService;
    private final ObjectMapper objectMapper;

    // ══════════════════════════════════════════════════════════════
    //  Main entry point
    // ══════════════════════════════════════════════════════════════

    public fieldmapping autoMap(UUID ocrID, UUID templateId) throws Exception {

        // ── Load OCR result and template ───────────────────────────
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
        log.info("=== Placeholders to match: {} ===", placeholders.size());
        placeholders.forEach(p -> log.info("  '{}'", p));

        // Initialise all placeholders to empty
        Map<String, String> mappedFields = new LinkedHashMap<>();
        for (String ph : placeholders) mappedFields.put(ph, "");

        // ── Pass 0: Gemini structured extraction ───────────────────
        log.info("=== Pass 0 — Gemini Structured Extraction ===");
        try {
            Map<String, Object> structured = geminiService.extractDocumentStructure(
                ocr.getExtractedText());

            if (!structured.isEmpty()) {
                Map<String, String> flatMap = flattenStructuredJson(structured);
                log.info("Pass 0 flat map entries: {}", flatMap.size());

                for (String ph : placeholders) {
                    String key = ph.replace("[", "").replace("]", "")
                        .replace("_", " ").toLowerCase();
                    String val = findBestMatchInFlatMap(key, flatMap);
                    if (val != null && !val.isBlank()) {
                        mappedFields.put(ph, val);
                        log.info("Pass 0 mapped: {} -> {}", ph, val);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Pass 0 failed (non-fatal, continuing to Pass 1): {}", e.getMessage());
        }

        long afterPass0 = mappedFields.values().stream()
            .filter(v -> v != null && !v.isBlank())
            .count();
        log.info("=== Pass 0 complete: {}/{} resolved ===", afterPass0, placeholders.size());

        // ── Pass 1: String/regex matching ──────────────────────────
        log.info("=== Pass 1 — String Matching ===");
        for (String placeholder : placeholders) {
            String currentVal = mappedFields.get(placeholder);
            if (currentVal != null && !currentVal.isBlank()) continue; // already filled by Pass 0

            String key = placeholder.replace("[", "").replace("]", "")
                .replace("_", " ").toLowerCase();

            String matched = findBestMatch(key, ocrKeyValues);
            if (matched != null && !matched.isBlank()) {
                mappedFields.put(placeholder, matched);
                log.info("Pass 1 mapped: {} -> {}", placeholder, matched);
            }
        }

        long afterPass1 = mappedFields.values().stream()
            .filter(v -> v != null && !v.isBlank())
            .count();
        log.info("=== Pass 1 complete: {}/{} resolved ===", afterPass1, placeholders.size());

        // ── Pass 2: Gemini fallback for still-empty placeholders ───
        List<String> unmapped = mappedFields.entrySet().stream()
            .filter(e -> e != null
                && e.getKey() != null
                && (e.getValue() == null || e.getValue().isBlank()))
            .map(e -> e.getKey())
            .filter(k -> k != null)
            .collect(Collectors.toList());

        if (!unmapped.isEmpty()) {
            log.info("=== Pass 2 — Gemini Fallback for {}/{} empty placeholders ===",
                unmapped.size(), placeholders.size());

            Map<String, String> partialResults = mappedFields.entrySet().stream()
                .filter(e -> e != null
                    && e.getKey() != null
                    && e.getValue() != null
                    && !e.getValue().isBlank())
                .collect(Collectors.toMap(
                    e -> e.getKey(),
                    e -> e.getValue(),
                    (a, b) -> a,
                    LinkedHashMap::new));

            try {
                Map<String, String> geminiResults = geminiService.mapOcrToPlaceholders(
                    ocr.getExtractedText(), unmapped, partialResults);

                geminiResults.forEach((ph, val) -> {
                    if (ph != null
                            && mappedFields.containsKey(ph)
                            && val != null
                            && !val.isBlank()) {
                        String existing = mappedFields.get(ph);
                        if (existing == null || existing.isBlank()) {
                            mappedFields.put(ph, val);
                            log.info("Pass 2 (Gemini) filled: {} -> {}", ph, val);
                        }
                    }
                });

            } catch (Exception e) {
                log.warn("Pass 2 Gemini failed (non-fatal, Pass 0+1 results preserved): {}",
                    e.getMessage());
            }
        } else {
            log.info("=== Pass 2 skipped — all placeholders resolved ===");
        }

        long afterPass2 = mappedFields.values().stream()
            .filter(v -> v != null && !v.isBlank())
            .count();
        log.info("=== Pass 2 complete: {}/{} resolved ===", afterPass2, placeholders.size());

        // ── Regex Validation ───────────────────────────────────────
        log.info("=== Regex Validation ===");
        Map<String, ValidationResult> regexResults =
            regexValidationService.validateAll(mappedFields);

        Map<String, ValidationResult> regexFailures =
            regexValidationService.getFailures(regexResults);

        Map<String, ValidationResult> regexWarnings = new LinkedHashMap<>();
        regexResults.forEach((ph, result) -> {
            if (result != null && result.isWarning()) regexWarnings.put(ph, result);
        });

        if (!regexFailures.isEmpty()) {
            log.warn("Regex validation — {} field(s) failed:", regexFailures.size());
            regexFailures.forEach((ph, r) -> {
                if (r != null) log.warn("  {} → {}", ph, r.getMessage());
            });
        }
        if (!regexWarnings.isEmpty()) {
            log.warn("Regex validation — {} field(s) with warnings:", regexWarnings.size());
            regexWarnings.forEach((ph, r) -> {
                if (r != null) log.warn("  {} → {}", ph, r.getMessage());
            });
        }
        if (regexFailures.isEmpty() && regexWarnings.isEmpty()) {
            log.info("Regex validation passed — all fields valid");
        }

        // ── Business Validation ────────────────────────────────────
        log.info("=== Business Validation ===");
        List<BusinessViolation> businessViolations =
            businessValidationService.validate(mappedFields);

        List<BusinessViolation> businessErrors =
            businessValidationService.getErrors(businessViolations);
        List<BusinessViolation> businessWarnings =
            businessValidationService.getWarnings(businessViolations);

        if (!businessErrors.isEmpty()) {
            log.warn("Business validation — {} error(s):", businessErrors.size());
            businessErrors.forEach(v -> {
                if (v != null) log.warn("  [{}] {}", v.getRule(), v.getMessage());
            });
        }
        if (!businessWarnings.isEmpty()) {
            log.warn("Business validation — {} warning(s):", businessWarnings.size());
            businessWarnings.forEach(v -> {
                if (v != null) log.warn("  [{}] {}", v.getRule(), v.getMessage());
            });
        }
        if (businessViolations.isEmpty()) {
            log.info("Business validation passed — all rules satisfied");
        }

        // ── Build validation summary for storage ───────────────────
        Map<String, Object> validationSummary = buildValidationSummary(
            regexResults, businessViolations);

        // ── Determine mapping status ───────────────────────────────
        String status;
        if (!regexFailures.isEmpty() || !businessErrors.isEmpty()) {
            status = "validation_failed";
            log.warn("=== Mapping status: VALIDATION_FAILED — staff must review errors ===");
        } else if (!regexWarnings.isEmpty() || !businessWarnings.isEmpty()) {
            status = "pending_with_warnings";
            log.warn("=== Mapping status: PENDING_WITH_WARNINGS — staff should review ===");
        } else {
            status = "pending";
            log.info("=== Mapping status: PENDING — ready for staff confirmation ===");
        }

        // ── Final summary ──────────────────────────────────────────
        long totalResolved = mappedFields.values().stream()
            .filter(v -> v != null && !v.isBlank())
            .count();
        log.info("=== Mapping complete: {}/{} placeholders resolved | Status: {} ===",
            totalResolved, placeholders.size(), status);

        // ── Save ───────────────────────────────────────────────────
        fieldmapping mapping = new fieldmapping();
        mapping.setOcr(ocr);
        mapping.setLetterTemplate(template);
        mapping.setMappedFields(objectMapper.writeValueAsString(mappedFields));
        mapping.setValidationSummary(objectMapper.writeValueAsString(validationSummary));
        mapping.setStatus(status);
        mapping.setCreatedAt(OffsetDateTime.now());

        return fieldMappingRepository.save(mapping);
    }

    // ══════════════════════════════════════════════════════════════
    //  Validation summary builder
    // ══════════════════════════════════════════════════════════════

    private Map<String, Object> buildValidationSummary(
            Map<String, ValidationResult> regexResults,
            List<BusinessViolation> businessViolations) {

        Map<String, Object> summary = new LinkedHashMap<>();

        // Regex results per field
        Map<String, Map<String, String>> regexSummary = new LinkedHashMap<>();
        regexResults.forEach((ph, result) -> {
            if (result == null) return;
            Map<String, String> detail = new LinkedHashMap<>();
            detail.put("status",  result.getStatus().name());
            detail.put("message", result.getMessage());
            regexSummary.put(ph, detail);
        });
        summary.put("regexValidation", regexSummary);

        // Business violations
        List<Map<String, String>> bizList = new ArrayList<>();
        businessViolations.forEach(v -> {
            if (v == null) return;
            Map<String, String> detail = new LinkedHashMap<>();
            detail.put("severity", v.getSeverity().name());
            detail.put("rule",     v.getRule());
            detail.put("message",  v.getMessage());
            bizList.add(detail);
        });
        summary.put("businessValidation", bizList);

        // Overall counts
        long regexFails = regexResults.values().stream()
            .filter(r -> r != null && !r.isValid())
            .count();

        long regexWarns = regexResults.values().stream()
            .filter(r -> r != null && r.isWarning())
            .count();

        long bizErrors = businessViolations.stream()
            .filter(v -> v != null
                && v.getSeverity() == BusinessViolation.Severity.ERROR)
            .count();

        long bizWarns = businessViolations.stream()
            .filter(v -> v != null
                && v.getSeverity() == BusinessViolation.Severity.WARNING)
            .count();

        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("regexFailures",    regexFails);
        counts.put("regexWarnings",    regexWarns);
        counts.put("businessErrors",   bizErrors);
        counts.put("businessWarnings", bizWarns);
        summary.put("counts", counts);

        return summary;
    }

    // ══════════════════════════════════════════════════════════════
    //  Other public methods
    // ══════════════════════════════════════════════════════════════

    public fieldmapping getMappingById(UUID mappingId) {
        return fieldMappingRepository.findById(mappingId)
            .orElseThrow(() -> new RuntimeException("Mapping not found: " + mappingId));
    }

    public fieldmapping confirmMapping(UUID mappingId,
            Map<String, String> confirmedFields) throws Exception {

        fieldmapping mapping = fieldMappingRepository.findById(mappingId)
            .orElseThrow(() -> new RuntimeException("Mapping not found: " + mappingId));

        Map<String, ValidationResult> regexResults =
            regexValidationService.validateAll(confirmedFields);
        List<BusinessViolation> businessViolations =
            businessValidationService.validate(confirmedFields);

        if (!regexValidationService.getFailures(regexResults).isEmpty()) {
            throw new RuntimeException(
                "Cannot confirm — regex validation still has failures. " +
                "Please fix all required fields before confirming.");
        }
        if (businessValidationService.hasCriticalViolations(businessViolations)) {
            throw new RuntimeException(
                "Cannot confirm — business validation has critical errors. " +
                "Please resolve all errors before confirming.");
        }

        mapping.setMappedFields(objectMapper.writeValueAsString(confirmedFields));
        mapping.setValidationSummary(objectMapper.writeValueAsString(
            buildValidationSummary(regexResults, businessViolations)));
        mapping.setStatus("confirmed");
        return fieldMappingRepository.save(mapping);
    }

    public List<fieldmapping> getMappingByOcr(UUID ocrId) {
        return fieldMappingRepository.findByOcr_OcrId(ocrId);
    }

    // ══════════════════════════════════════════════════════════════
    //  Pass 0 helpers — flatten Gemini structured JSON
    // ══════════════════════════════════════════════════════════════

    private Map<String, String> flattenStructuredJson(Map<String, Object> structured) {
        Map<String, String> flat = new LinkedHashMap<>();
        flattenRecursive("", structured, flat);
        return flat;
    }

    @SuppressWarnings("unchecked")
    private void flattenRecursive(String prefix, Object obj, Map<String, String> flat) {
        if (obj instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) obj;
            map.forEach((k, v) -> {
                if (k == null) return;
                String newKey = prefix.isEmpty()
                    ? k.toLowerCase()
                    : prefix + " " + k.toLowerCase();
                flattenRecursive(newKey, v, flat);
            });
        } else if (obj instanceof List) {
            List<?> list = (List<?>) obj;
            for (int i = 0; i < list.size(); i++) {
                Object item = list.get(i);
                if (item != null) {
                    flattenRecursive(prefix + " " + i, item, flat);
                }
            }
        } else if (obj != null && !obj.toString().isBlank()) {
            flat.put(prefix, obj.toString().trim());
        }
    }

    private String findBestMatchInFlatMap(String placeholderKey,
                                           Map<String, String> flatMap) {
        if (flatMap.containsKey(placeholderKey)) return flatMap.get(placeholderKey);

        for (Map.Entry<String, String> entry : flatMap.entrySet()) {
            if (entry.getKey() == null) continue;
            if (entry.getKey().contains(placeholderKey)
                    || placeholderKey.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        String bestVal   = null;
        double bestScore = 0.6;
        for (Map.Entry<String, String> entry : flatMap.entrySet()) {
            if (entry.getKey() == null) continue;
            double score =
                (cosineSimilarity(placeholderKey, entry.getKey()) * 0.7)
              + (levenshteinSimilarity(placeholderKey, entry.getKey()) * 0.3);
            if (score > bestScore) {
                bestScore = score;
                bestVal   = entry.getValue();
            }
        }
        return bestVal;
    }

    // ══════════════════════════════════════════════════════════════
    //  Pass 1 helpers — OCR key-value extraction & matching
    // ══════════════════════════════════════════════════════════════

    public Map<String, String> extractKeyValues(String ocrText) {
        Map<String, String> keyValues = new LinkedHashMap<>();
        if (ocrText == null || ocrText.isEmpty()) return keyValues;

        String[] lines      = ocrText.split("\\n");
        Pattern  kvPattern  = Pattern.compile("^(.+?)\\s*[:;|]+\\s*(.+)$");

        String        lastKey  = null;
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
                    keyValues.put(lastKey,
                        (existing != null ? existing : "") + " " + line);
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

    private String findBestMatch(String placeholderKey,
                                  Map<String, String> ocrKeyValues) {
        if (ocrKeyValues.containsKey(placeholderKey)) {
            return ocrKeyValues.get(placeholderKey);
        }

        String phKey = placeholderKey.replaceAll("[^a-z0-9\\s]", "").trim();

        String bestMatchValue = null;
        double bestScore      = 0.5;

        for (Map.Entry<String, String> entry : ocrKeyValues.entrySet()) {
            if (entry.getKey() == null) continue;
            String ocrKey = entry.getKey().replaceAll("[^a-z0-9\\s]", "").trim();

            if (ocrKey.equals(phKey) || ocrKey.contains(phKey) || phKey.contains(ocrKey)) {
                return entry.getValue();
            }

            double cosine      = cosineSimilarity(phKey, ocrKey);
            double levenshtein = levenshteinSimilarity(phKey, ocrKey);
            double combined    = (cosine * 0.7) + (levenshtein * 0.3);

            log.info("  '{}' vs '{}' → cosine={}, lev={}, combined={}",
                phKey, ocrKey,
                String.format("%.2f", cosine),
                String.format("%.2f", levenshtein),
                String.format("%.2f", combined));

            if (combined > bestScore) {
                bestScore      = combined;
                bestMatchValue = entry.getValue();
            }
        }
        return bestMatchValue;
    }

    // ══════════════════════════════════════════════════════════════
    //  Similarity utilities
    // ══════════════════════════════════════════════════════════════

    private double cosineSimilarity(String s1, String s2) {
        Map<String, Integer> vec1 = wordVector(s1);
        Map<String, Integer> vec2 = wordVector(s2);

        double dotProduct = 0.0;
        for (Map.Entry<String, Integer> entry : vec1.entrySet()) {
            Integer v2val = vec2.get(entry.getKey());
            if (v2val != null) {
                dotProduct += entry.getValue() * v2val;
            }
        }

        double mag1 = Math.sqrt(vec1.values().stream().mapToDouble(v -> v * v).sum());
        double mag2 = Math.sqrt(vec2.values().stream().mapToDouble(v -> v * v).sum());

        if (mag1 == 0 || mag2 == 0) return 0.0;
        return dotProduct / (mag1 * mag2);
    }

    private Map<String, Integer> wordVector(String text) {
        Map<String, Integer> vector = new HashMap<>();
        for (String word : text.split("\\s+")) {
            if (word != null && !word.isEmpty()) {
                vector.merge(word, 1,(a, b) -> a + b);
            }
        }
        return vector;
    }

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