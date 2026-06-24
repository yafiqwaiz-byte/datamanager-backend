package dev.waiz.datamanager.service;

import java.util.List;
import java.util.Map;


import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.*;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import java.util.LinkedHashMap;


@Service
@RequiredArgsConstructor
@Slf4j
public class GeminiService {

    @Value("${gemini.api.key}")
    private String apiKey;

    @Value("${gemini.api.url}")
    private String apiUrl;

    private final ObjectMapper objectMapper;
    private final OkHttpClient httpClient = new OkHttpClient();

    // ── Existing: dashboard analysis ──────────────────────────────────────────
    public Map<String,Object> analyzeAndSuggestDashboard(List<String> columns,
            List<Map<String,Object>> sampleRows) throws Exception {
        String prompt = buildPrompt(columns, sampleRows);
        String response = callGemini(prompt);
        return parseGeminiResponse(response);
    }

    // ── New: OCR → placeholder mapping ───────────────────────────────────────
    // Called by FieldMappingService for placeholders that the string-matching
    // pass left empty or couldn't confidently resolve.
    //
    // Parameters:
    //   ocrText          — the full raw OCR text from the uploaded image
    //   unmappedPhs      — placeholders that still need a value (e.g. "[TARIKH]")
    //   partialResults   — what the string-matching pass already found, so Gemini
    //                      has context and doesn't re-guess already-resolved fields
    //
    // Returns a Map<placeholder, value> for the unmapped ones only.
    // Any placeholder Gemini can't find is returned with an empty string so
    // the caller can still show it as a blank field rather than crashing.
    @SuppressWarnings("unchecked")
    public Map<String, String> mapOcrToPlaceholders(
            String ocrText,
            List<String> unmappedPhs,
            Map<String, String> partialResults) throws Exception {

        String prompt = buildOcrMappingPrompt(ocrText, unmappedPhs, partialResults);
        String rawResponse = callGemini(prompt);

        // Parse JSON response
        try {
            String cleaned = rawResponse
                .replace("```json", "")
                .replace("```", "")
                .trim();

            int start = cleaned.indexOf("{");
            int end   = cleaned.lastIndexOf("}");
            if (start == -1 || end == -1) {
                log.warn("Gemini OCR mapping returned no JSON — raw: {}", cleaned);
                return emptyMap(unmappedPhs);
            }

            Map<String, String> result = objectMapper.readValue(
                cleaned.substring(start, end + 1), Map.class);

            // Ensure every requested placeholder has an entry (even if blank)
            // so the caller never gets a NullPointerException
            for (String ph : unmappedPhs) {
                result.putIfAbsent(ph, "");
            }

            log.info("Gemini resolved {}/{} unmapped placeholders",
                result.values().stream().filter(v -> !v.isBlank()).count(),
                unmappedPhs.size());

            return result;

        } catch (Exception e) {
            log.warn("Failed to parse Gemini OCR mapping response: {}", e.getMessage());
            return emptyMap(unmappedPhs);
        }
    }

    private String buildOcrMappingPrompt(
            String ocrText,
            List<String> unmappedPlaceholders,
            Map<String, String> partialResults) {

        StringBuilder sb = new StringBuilder();

        sb.append("You are an expert document parser for Malaysian government and corporate documents.\n");
        sb.append("Your task is to extract specific field values from OCR-extracted text.\n\n");

        sb.append("CONTEXT — what has already been extracted by string matching:\n");
        if (partialResults.isEmpty()) {
            sb.append("  (nothing extracted yet)\n");
        } else {
            partialResults.forEach((k, v) ->
                sb.append("  ").append(k).append(" = \"").append(v).append("\"\n"));
        }
        sb.append("\n");

        sb.append("FULL OCR TEXT (may contain noise, line breaks, Malay/English mix):\n");
        sb.append("---START---\n");
        sb.append(ocrText);
        sb.append("\n---END---\n\n");

        sb.append("PLACEHOLDERS TO RESOLVE (you must find a value for each):\n");
        for (String ph : unmappedPlaceholders) {
            // Convert [NAMA_PEKERJA] → "NAMA PEKERJA" as a hint
            String hint = ph.replace("[", "").replace("]", "").replace("_", " ");
            sb.append("  ").append(ph).append("  (meaning: ").append(hint).append(")\n");
        }
        sb.append("\n");

        sb.append("RULES:\n");
        sb.append("1. Use the OCR text to find the best matching value for each placeholder.\n");
        sb.append("2. Handle common Malay abbreviations: ");
        sb.append("Tkh/Tarikh=date, Bil=number/reference, No=number, ");
        sb.append("Nama=name, Syarikat=company, Alamat=address, ");
        sb.append("Tel=phone, Faks=fax, Jawatan=position/title.\n");
        sb.append("3. If the OCR text has noise (e.g. 'Nam4' instead of 'Nama'), ");
        sb.append("still try to match it.\n");
        sb.append("4. For date fields, return the date in the format found in the document.\n");
        sb.append("5. If a value genuinely cannot be found, return an empty string \"\" for it.\n");
        sb.append("6. Do NOT invent values — only extract what is actually in the OCR text.\n");
        sb.append("7. Do NOT re-map placeholders already in the CONTEXT above.\n\n");

        sb.append("Respond ONLY with a valid JSON object mapping each placeholder to its value.\n");
        sb.append("No markdown, no explanation, no text before or after the JSON.\n");
        sb.append("Example format:\n");
        sb.append("{\n");
        sb.append("  \"[TARIKH]\": \"15 Januari 2026\",\n");
        sb.append("  \"[NAMA_PEKERJA]\": \"Ahmad bin Ali\",\n");
        sb.append("  \"[NOMBOR_RUJUKAN]\": \"\"\n");
        sb.append("}\n");

        return sb.toString();
    }

    private Map<String, String> emptyMap(List<String> placeholders) {
        Map<String, String> result = new LinkedHashMap<>();
        placeholders.forEach(ph -> result.put(ph, ""));
        return result;
    }

    // ── Shared: call Gemini API ───────────────────────────────────────────────
    private String callGemini(String prompt) throws Exception {
        Map<String,Object> requestBody = Map.of(
            "contents", List.of(
                Map.of("parts", List.of(
                    Map.of("text", prompt)
                ))
            )
        );

        String jsonBody = objectMapper.writeValueAsString(requestBody);

        Request request = new Request.Builder()
            .url(apiUrl + "?key=" + apiKey)
            .post(RequestBody.create(jsonBody, MediaType.parse("application/json")))
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (response.code() == 429) {
                throw new RuntimeException("AI service is busy. Please wait 1 minute and try again.");
            }
            if (!response.isSuccessful()) {
                throw new RuntimeException("Gemini API error: " + response.code());
            }
            String responseBody = response.body().string();
            log.info("Gemini raw response: {}", responseBody);

            JsonNode root = objectMapper.readTree(responseBody);
            String text = root.path("candidates")
                .get(0)
                .path("content")
                .path("parts")
                .get(0)
                .path("text")
                .asText();

            log.info("Gemini extracted text: {}", text);
            return text;
        }
    }

    // ── Existing: dashboard prompt builder ────────────────────────────────────
    private String buildPrompt(List<String> columns, List<Map<String,Object>> sampleRows) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are a data analyst for TNB (Tenaga Nasional Berhad) ");
        sb.append("SBU Asset Development (AD) North Region.\n");
        sb.append("North Region covers: Perlis, Pulau Pinang, Kedah, Perak (Services unit only).\n\n");
        
        sb.append("Known TNB report types:\n");
        sb.append("1. PO Ageing - Purchase Order ageing tracking (columns: Zone, PO No, ");
        sb.append("Outstanding Amount, Days Outstanding, Resolved, Blocker, Voltage, State)\n");
        sb.append("2. SN Management - Service Notification tracking (columns: SN No, ");
        sb.append("Zone, State, Voltage, Status NC04/NC05/NOCO, CSP Amount, PIC)\n");
        sb.append("3. CAPEX Performance - Capital expenditure tracking\n");
        sb.append("4. Bank Draft - Bank draft recovery tracking\n");
        sb.append("5. PF/RC/OEI-OPC - Power factor/reactive compensation tracking\n\n");

        sb.append("TNB Target System:\n");
        sb.append("- LMT = Lower Management Target (minimum acceptable target)\n");
        sb.append("- UMT = Upper Management Target (stretch/ambitious target)\n");
        sb.append("- Status UMT (green) = achieved upper target ✅\n");
        sb.append("- Status MT (yellow) = achieved LMT but not UMT ⚠️\n");
        sb.append("- Status Below LMT (red) = failed minimum target ❌\n");
        sb.append("- Performance is measured as: Current Performance vs LMT vs UMT\n\n");

        sb.append("TNB KPI Categories (Balanced Scorecard):\n");
        sb.append("- Financial: Cost efficiency, EBIT, Bank Draft Recovery, CAPEX\n");
        sb.append("- Customer: CSI, SAIDI, DN ReOrg\n");
        sb.append("- Internal Process: CAPEX delivery, IBR PI rehab, SAIDI Cities\n");
        sb.append("- Learning & Growth: Zero Fatality, employee development\n\n");

        sb.append("Analyze this dataset and identify what type of TNB report it is.\n\n");
    
        sb.append("Columns found: ").append(columns).append("\n\n");
        sb.append("Sample data (first 3 rows):\n");

        for (int i = 0; i < Math.min(3, sampleRows.size()); i++) {
            sb.append(sampleRows.get(i)).append("\n");
        }

        sb.append("\nRespond ONLY in this JSON format, no markdown:\n");
        sb.append("{\n");
        sb.append("  \"datasetType\": \"PO Ageing / SN Management / CAPEX Performance / Bank Draft / PF/RC/OEI-OPC / Unknown\",\n");
        sb.append("  \"datasetDescription\": \"brief description of what this data is about\",\n");
        sb.append("  \"region\": \"North (Perlis/Pulau Pinang/Kedah/Perak)\",\n");
        sb.append("  \"suggestedCharts\": [\n");
        sb.append("    {\n");
        sb.append("      \"title\": \"Chart title\",\n");
        sb.append("      \"type\": \"bar/pie/number/line/table\",\n");
        sb.append("      \"column\": \"exact column name from data\",\n");
        sb.append("      \"groupBy\": \"column to group by or null\",\n");
        sb.append("      \"aggregation\": \"sum/count/average/percentage\",\n");
        sb.append("      \"description\": \"why this chart is useful for TNB management\",\n");
        sb.append("      \"priority\": \"high/medium/low\"\n");
        sb.append("    }\n");
        sb.append("  ],\n");
        sb.append("  \"kpiMetrics\": [\n");
        sb.append("    {\n");
        sb.append("      \"name\": \"KPI metric name\",\n");
        sb.append("      \"column\": \"column to calculate from\",\n");
        sb.append("      \"calculation\": \"how to calculate (sum/count/percentage)\",\n");
        sb.append("      \"target\": \"LMT or UMT target if identifiable\"\n");
        sb.append("    }\n");
        sb.append("  ],\n");
        sb.append("  \"suggestedFilters\": [\n");
        sb.append("    {\n");
        sb.append("      \"column\": \"column name\",\n");
        sb.append("      \"filterType\": \"dropdown/date/range\",\n");
        sb.append("      \"description\": \"what this filter helps with\"\n");
        sb.append("    }\n");
        sb.append("  ],\n");
        sb.append("  \"keyInsights\": [\"insight 1\", \"insight 2\", \"insight 3\"],\n");
        sb.append("  \"dataQuality\": {\n");
        sb.append("    \"numericColumns\": [\"columns suitable for sum/average\"],\n");
        sb.append("    \"categoricalColumns\": [\"columns suitable for grouping\"],\n");
        sb.append("    \"dateColumns\": [\"date/time columns\"]\n");
        sb.append("  },\n");
        sb.append("  \"recommendedColumns\": [\"most important columns for this report\"],\n");
        sb.append("  \"businessSummary\": \"one paragraph summary for TNB management\"\n");
        sb.append("}\n");

        sb.append("\nCRITICAL: Your response must start with { and end with }.");
        sb.append(" Do NOT include any text before or after the JSON.");
        sb.append(" Do NOT use markdown. Do NOT explain anything.\n");

        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private Map<String,Object> parseGeminiResponse(String response) {
        try {
            String cleaned = response
                .replace("```json", "")
                .replace("```", "")
                .trim();

            int jsonStart = cleaned.indexOf("{");
            int jsonEnd   = cleaned.lastIndexOf("}");

            if (jsonStart == -1 || jsonEnd == -1) {
                log.error("No JSON found in response: {}", cleaned);
                return Map.of("error", "AI returned invalid response");
            }
            String jsonOnly = cleaned.substring(jsonStart, jsonEnd + 1);
            return objectMapper.readValue(jsonOnly, Map.class);
        } catch (Exception e) {
            log.error("Failed to parse Gemini response: {}", e.getMessage());
            return Map.of("error", "Failed to parse AI response");
        }
    }
}