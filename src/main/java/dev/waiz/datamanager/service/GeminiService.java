package dev.waiz.datamanager.service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class GeminiService {

    @Value("${gemini.api.key}")
    private String apiKey;

    @Value("${gemini.api.url}")
    private String apiUrl;

    private final ObjectMapper objectMapper;

    // ── OkHttpClient as singleton bean with timeouts ───────────────
    private final OkHttpClient httpClient = new OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)   // max time to establish connection
        .readTimeout(60,    TimeUnit.SECONDS)   // max time to wait for response body
        .writeTimeout(30,   TimeUnit.SECONDS)   // max time to send request body
        .build();

    // ── Retry config ───────────────────────────────────────────────
    private static final int    MAX_RETRIES    = 3;
    private static final long   INITIAL_DELAY  = 2000L; // 2 seconds

    // ══════════════════════════════════════════════════════════════
    //  PO Aging Dashboard Analysis
    // ══════════════════════════════════════════════════════════════

    public Map<String, Object> analyzePOAgingDashboard(
            Map<String, Object> dashboardData) throws Exception {

        String prompt   = buildPOAgingPrompt(dashboardData);
        String response = callGeminiWithRetry(prompt);
        return parseGeminiResponse(response);
    }

    private String buildPOAgingPrompt(Map<String, Object> data) {
        StringBuilder sb = new StringBuilder();

        sb.append("You are a financial analyst for TNB (Tenaga Nasional Berhad) ");
        sb.append("SBU Asset Development (AD) Northern Region.\n");
        sb.append("The Northern Region covers: Perlis, Pulau Pinang, Kedah, and Perak.\n\n");

        sb.append("You are analyzing a PO Aging Dashboard that tracks outstanding ");
        sb.append("Purchase Orders (POs) that have exceeded 180 days.\n\n");

        sb.append("MARK SYSTEM (percentile-based, bias-free):\n");
        sb.append("  Mark 1 = High Aging   (% aging > 66th percentile) → RED    ❌\n");
        sb.append("  Mark 2 = Medium Aging (% aging > 33rd percentile) → YELLOW ⚠️\n");
        sb.append("  Mark 3 = Low Aging    (% aging ≤ 33rd percentile) → GREEN  ✅\n");
        sb.append("  Stations with 0 PO > 180 days automatically get Mark 3.\n\n");

        sb.append("SUBZONE STRUCTURE:\n");
        sb.append("  P1  = Pulau Pinang 1 (TNB Seberang Jaya, TNB Pulau Pinang)\n");
        sb.append("  P2  = Pulau Pinang 2 (TNB Nibong Tebal, TNB Bertam, TNB Bayan Baru)\n");
        sb.append("  SGP/KLM = Sungai Petani / Kulim (6 stations)\n");
        sb.append("  ALS/KAN = Alor Setar / Kangar (6 stations)\n");
        sb.append("  A1  = Perak A1 (6 stations — Ipoh, Ulu Kinta, Batu Gajah, etc)\n");
        sb.append("  A2  = Perak A2 (4 stations — Sri Manjung, Teluk Intan, etc)\n");
        sb.append("  A3  = Perak A3 (4 stations — Taiping, Bagan Serai, etc)\n\n");

        sb.append("CURRENT DASHBOARD DATA:\n");
        sb.append("─────────────────────────────────────────\n");

        sb.append("KPI SUMMARY:\n");
        sb.append("  Total PO > 180 days (original):  ")
          .append(data.getOrDefault("totalPOOver180", "N/A")).append("\n");
        sb.append("  Total PO > 180 days (updated):   ")
          .append(data.getOrDefault("updatedTotalPOOver180", "N/A")).append("\n");
        sb.append("  Total Outstanding (original):    RM ")
          .append(data.getOrDefault("totalOutstandingValue", "N/A")).append("\n");
        sb.append("  Total Outstanding (updated):     RM ")
          .append(data.getOrDefault("updatedTotalOutstandingValue", "N/A")).append("\n");
        sb.append("  Average % Aging:                 ")
          .append(data.getOrDefault("averagePercentAging", "N/A")).append("%\n");
        sb.append("  High Aging Stations (Mark 1):    ")
          .append(data.getOrDefault("highAgingStations", "N/A")).append("\n");
        sb.append("  Medium Aging Stations (Mark 2):  ")
          .append(data.getOrDefault("mediumAgingStations", "N/A")).append("\n");
        sb.append("  Low Aging Stations (Mark 3):     ")
          .append(data.getOrDefault("lowAgingStations", "N/A")).append("\n");
        sb.append("  Total Stations:                  ")
          .append(data.getOrDefault("totalStations", "N/A")).append("\n");
        sb.append("  POs Fully Cleared:               ")
          .append(data.getOrDefault("totalPOCleared", "N/A")).append("\n");
        sb.append("  POs Partially Paid:              ")
          .append(data.getOrDefault("totalPOPartiallyPaid", "N/A")).append("\n");
        sb.append("  Total Cleared Amount:            RM ")
          .append(data.getOrDefault("totalClearedAmount", "N/A")).append("\n");
        sb.append("  33rd Percentile Threshold:       ")
          .append(data.getOrDefault("percentile33", "N/A")).append("%\n");
        sb.append("  66th Percentile Threshold:       ")
          .append(data.getOrDefault("percentile66", "N/A")).append("%\n\n");

        Object stationData = data.get("stationData");
        if (stationData instanceof List) {
            sb.append("STATION BREAKDOWN (top stations by PO count):\n");
            List<?> stations = (List<?>) stationData;
            int limit = Math.min(10, stations.size());
            for (int i = 0; i < limit; i++) {
                Object s = stations.get(i);
                if (s instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> station = (Map<String, Object>) s;
                    sb.append("  ").append(station.getOrDefault("stationName", "?"))
                      .append(" | BA: ").append(station.getOrDefault("busArea", "?"))
                      .append(" | Subzone: ").append(station.getOrDefault("subzone", "?"))
                      .append(" | PO>180: ").append(station.getOrDefault("updatedCountPOOver180", "?"))
                      .append(" | Outstanding: RM ").append(station.getOrDefault("updatedOutstandingValue", "?"))
                      .append(" | % Aging: ").append(station.getOrDefault("updatedPercentAging", "?"))
                      .append("% | Mark: ").append(station.getOrDefault("updatedMarks", "?"));
                    Object remarks = station.get("remarks");
                    if (remarks != null && !remarks.toString().isEmpty()) {
                        sb.append(" | ").append(remarks);
                    }
                    sb.append("\n");
                }
            }
            sb.append("\n");
        }

        Object subzoneSummary = data.get("subzoneSummary");
        if (subzoneSummary instanceof List) {
            sb.append("SUBZONE SUMMARY:\n");
            List<?> subzones = (List<?>) subzoneSummary;
            for (Object sz : subzones) {
                if (sz instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> subzone = (Map<String, Object>) sz;
                    sb.append("  ").append(subzone.getOrDefault("subzone", "?"))
                      .append(" (").append(subzone.getOrDefault("subzoneLabel", "?")).append(")")
                      .append(" | Stations: ").append(subzone.getOrDefault("totalStations", "?"))
                      .append(" | PO>180: ").append(subzone.getOrDefault("updatedTotalPOOver180", "?"))
                      .append(" | Outstanding: RM ").append(subzone.getOrDefault("updatedOutstandingValue", "?"))
                      .append(" | Mark: ").append(subzone.getOrDefault("marks", "?"))
                      .append("\n");
                }
            }
            sb.append("\n");
        }

        sb.append("─────────────────────────────────────────\n\n");
        sb.append("Based on the above PO Aging data, provide a comprehensive ");
        sb.append("management analysis in the following JSON format.\n\n");
        sb.append("Respond ONLY with valid JSON, no markdown, no explanation:\n");
        sb.append("{\n");
        sb.append("  \"executiveSummary\": \"2-3 sentence summary for senior management\",\n");
        sb.append("  \"overallStatus\": \"Critical / Concerning / Moderate / Good\",\n");
        sb.append("  \"criticalStations\": [\n");
        sb.append("    {\n");
        sb.append("      \"station\": \"station name\",\n");
        sb.append("      \"reason\": \"why this station is critical\",\n");
        sb.append("      \"urgency\": \"Immediate / High / Medium\"\n");
        sb.append("    }\n");
        sb.append("  ],\n");
        sb.append("  \"subzoneAnalysis\": [\n");
        sb.append("    {\n");
        sb.append("      \"subzone\": \"subzone code\",\n");
        sb.append("      \"status\": \"assessment of this subzone\",\n");
        sb.append("      \"recommendation\": \"specific action for this subzone\"\n");
        sb.append("    }\n");
        sb.append("  ],\n");
        sb.append("  \"keyRisks\": [\"risk 1\", \"risk 2\", \"risk 3\"],\n");
        sb.append("  \"positiveObservations\": [\"positive finding 1\", \"positive finding 2\"],\n");
        sb.append("  \"recommendedActions\": [\n");
        sb.append("    {\n");
        sb.append("      \"action\": \"specific action to take\",\n");
        sb.append("      \"priority\": \"High / Medium / Low\",\n");
        sb.append("      \"target\": \"which station or subzone\",\n");
        sb.append("      \"timeline\": \"Immediate / 1 week / 1 month\"\n");
        sb.append("    }\n");
        sb.append("  ],\n");
        sb.append("  \"financialImpact\": \"assessment of total outstanding value and clearing progress\",\n");
        sb.append("  \"clearingProgress\": \"assessment of how well cleared POs are being processed\",\n");
        sb.append("  \"trendAssessment\": \"overall trend — improving, stable, or worsening\"\n");
        sb.append("}\n\n");
        sb.append("CRITICAL: Start with { and end with }. No text before or after JSON.\n");

        return sb.toString();
    }

    // ══════════════════════════════════════════════════════════════
    //  Pass 0: Structured document extraction
    // ══════════════════════════════════════════════════════════════

    public Map<String, Object> extractDocumentStructure(String ocrText) throws Exception {

        String prompt      = buildStructuredExtractionPrompt(ocrText);
        String rawResponse = callGeminiWithRetry(prompt);

        try {
            String cleaned = rawResponse
                .replace("```json", "")
                .replace("```", "")
                .trim();

            int start = cleaned.indexOf("{");
            int end   = cleaned.lastIndexOf("}");
            if (start == -1 || end == -1) {
                log.warn("Pass 0 — no JSON in Gemini response");
                return Collections.emptyMap();
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> result = objectMapper.readValue(
                cleaned.substring(start, end + 1), Map.class);

            log.info("Pass 0 extraction complete — documentType: {}",
                result.getOrDefault("documentType", "unknown"));

            return result;

        } catch (Exception e) {
            log.warn("Pass 0 parse failed: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    private String buildStructuredExtractionPrompt(String ocrText) {
        StringBuilder sb = new StringBuilder();

        sb.append("You are an expert document parser for Malaysian government ");
        sb.append("and corporate documents (Malay/English).\n\n");
        sb.append("Extract structured information from the OCR text below.\n");
        sb.append("Return ONLY valid JSON, no markdown, no explanation.\n\n");
        sb.append("JSON structure to follow:\n");
        sb.append("{\n");
        sb.append("  \"documentType\": \"e.g. Leave Application / Appointment Letter / Invoice / Memo\",\n");
        sb.append("  \"sender\": {\n");
        sb.append("    \"name\": \"\", \"staffId\": \"\", \"company\": \"\",\n");
        sb.append("    \"position\": \"\", \"department\": \"\", \"email\": \"\", \"phone\": \"\"\n");
        sb.append("  },\n");
        sb.append("  \"recipient\": {\n");
        sb.append("    \"name\": \"\", \"position\": \"\", \"department\": \"\", \"company\": \"\"\n");
        sb.append("  },\n");
        sb.append("  \"subject\": \"\",\n");
        sb.append("  \"dates\": {\n");
        sb.append("    \"documentDate\": \"\", \"startDate\": \"\",\n");
        sb.append("    \"endDate\": \"\", \"effectiveDate\": \"\"\n");
        sb.append("  },\n");
        sb.append("  \"referenceNumber\": \"\",\n");
        sb.append("  \"purpose\": \"\",\n");
        sb.append("  \"details\": {\n");
        sb.append("    \"leaveType\": \"\", \"position\": \"\",\n");
        sb.append("    \"salary\": \"\", \"location\": \"\", \"duration\": \"\"\n");
        sb.append("  },\n");
        sb.append("  \"attachments\": [],\n");
        sb.append("  \"remarks\": \"\"\n");
        sb.append("}\n\n");
        sb.append("RULES:\n");
        sb.append("1. Detect language (Malay/English/mixed) automatically.\n");
        sb.append("2. Malay mappings: Tarikh=date, Nama=name, Jawatan=position,\n");
        sb.append("   Dari/Daripada=sender, Kepada=recipient, Perkara=subject,\n");
        sb.append("   Rujukan=reference, Bahagian/Jabatan=department, Gaji=salary.\n");
        sb.append("3. Leave empty string for fields not found — do NOT invent values.\n");
        sb.append("4. Dates: YYYY-MM-DD format if possible.\n");
        sb.append("5. Return only JSON: start with { end with }.\n\n");
        sb.append("OCR TEXT:\n---START---\n");
        sb.append(ocrText);
        sb.append("\n---END---\n");

        return sb.toString();
    }

    // ══════════════════════════════════════════════════════════════
    //  Pass 2: OCR → Placeholder Mapping fallback
    // ══════════════════════════════════════════════════════════════

    @SuppressWarnings("unchecked")
    public Map<String, String> mapOcrToPlaceholders(
            String ocrText,
            List<String> unmappedPhs,
            Map<String, String> partialResults) throws Exception {

        String prompt      = buildOcrMappingPrompt(ocrText, unmappedPhs, partialResults);
        String rawResponse = callGeminiWithRetry(prompt);

        try {
            String cleaned = rawResponse
                .replace("```json", "")
                .replace("```", "")
                .trim();

            int start = cleaned.indexOf("{");
            int end   = cleaned.lastIndexOf("}");
            if (start == -1 || end == -1) {
                log.warn("Pass 2 — no JSON in Gemini response");
                return emptyMap(unmappedPhs);
            }

            Map<String, String> result = objectMapper.readValue(
                cleaned.substring(start, end + 1), Map.class);

            for (String ph : unmappedPhs) {
                result.putIfAbsent(ph, "");
            }

            log.info("Pass 2 resolved {}/{} placeholders",
                result.values().stream().filter(v -> !v.isBlank()).count(),
                unmappedPhs.size());

            return result;

        } catch (Exception e) {
            log.warn("Pass 2 parse failed: {}", e.getMessage());
            return emptyMap(unmappedPhs);
        }
    }

    private String buildOcrMappingPrompt(
            String ocrText,
            List<String> unmappedPlaceholders,
            Map<String, String> partialResults) {

        StringBuilder sb = new StringBuilder();

        sb.append("You are an expert document parser for Malaysian government and corporate documents.\n");
        sb.append("You specialize in Malay/English mixed documents.\n\n");

        sb.append("CONTEXT — already extracted (DO NOT re-map):\n");
        if (partialResults.isEmpty()) {
            sb.append("  (nothing extracted yet)\n");
        } else {
            partialResults.forEach((k, v) ->
                sb.append("  ").append(k).append(" = \"").append(v).append("\"\n"));
        }
        sb.append("\n");

        sb.append("FULL OCR TEXT:\n---START---\n");
        sb.append(ocrText);
        sb.append("\n---END---\n\n");

        sb.append("UNRESOLVED PLACEHOLDERS:\n");
        for (String ph : unmappedPlaceholders) {
            String hint     = ph.replace("[", "").replace("]", "").replace("_", " ").toLowerCase();
            String malayHint = getMalayHint(hint);
            sb.append("  ").append(ph)
              .append("  (").append(hint)
              .append(malayHint.isEmpty() ? "" : " | " + malayHint)
              .append(")\n");
        }
        sb.append("\n");

        sb.append("RULES:\n");
        sb.append("1. Extract ONLY from OCR text — never invent values.\n");
        sb.append("2. Empty fields return \"\" — never null.\n");
        sb.append("3. Do NOT re-map already extracted fields above.\n");
        sb.append("4. Handle OCR noise: Nam4→Nama, 0→O in names, etc.\n");
        sb.append("5. Dates: exact format from document (e.g. '15 Januari 2026').\n");
        sb.append("6. Names: full name, preserve bin/binti/a/l/a/p.\n");
        sb.append("7. Reference numbers: preserve exact format.\n\n");

        sb.append("Malay abbreviations: Tarikh=date, Nama=name, Jawatan=position,\n");
        sb.append("Bahagian/Jabatan=department, No.Tel=phone, Bil./Rujukan=reference,\n");
        sb.append("Kepada=recipient, Daripada=sender, Perkara=subject, Gaji=salary,\n");
        sb.append("Tempoh=duration, Mulai=start date, Hingga=end date.\n\n");

        sb.append("RESPOND ONLY with valid JSON. Every placeholder MUST be a key.\n");
        sb.append("{\n");
        sb.append("  \"[TARIKH]\": \"15 Januari 2026\",\n");
        sb.append("  \"[NAMA_PEKERJA]\": \"Ahmad bin Ali\"\n");
        sb.append("}\n");
        sb.append("CRITICAL: Start with { end with }. No text before or after.\n");

        return sb.toString();
    }

    private String getMalayHint(String englishHint) {
        Map<String, String> hints = Map.ofEntries(
            Map.entry("name",             "Nama"),
            Map.entry("staff name",       "Nama Pekerja"),
            Map.entry("staff id",         "No. Pekerja / ID Pekerja"),
            Map.entry("position",         "Jawatan"),
            Map.entry("department",       "Bahagian / Jabatan"),
            Map.entry("date",             "Tarikh"),
            Map.entry("start date",       "Tarikh Mula / Mulai"),
            Map.entry("end date",         "Tarikh Akhir / Hingga"),
            Map.entry("effective date",   "Tarikh Berkuat Kuasa"),
            Map.entry("reference number", "No. Rujukan / Bil."),
            Map.entry("subject",          "Perkara / Perihal"),
            Map.entry("salary",           "Gaji / Emolumen"),
            Map.entry("company",          "Syarikat"),
            Map.entry("address",          "Alamat"),
            Map.entry("phone",            "No. Tel"),
            Map.entry("leave type",       "Jenis Cuti"),
            Map.entry("duration",         "Tempoh"),
            Map.entry("recipient",        "Kepada"),
            Map.entry("sender",           "Daripada / Dari"),
            Map.entry("remarks",          "Catatan / Ulasan")
        );
        return hints.getOrDefault(englishHint, "");
    }

    private Map<String, String> emptyMap(List<String> placeholders) {
        Map<String, String> result = new LinkedHashMap<>();
        placeholders.forEach(ph -> result.put(ph, ""));
        return result;
    }

    // ══════════════════════════════════════════════════════════════
    //  Core: call Gemini with exponential backoff retry
    // ══════════════════════════════════════════════════════════════

    private String callGeminiWithRetry(String prompt) throws Exception {
        int  attempt  = 0;
        long delayMs  = INITIAL_DELAY;

        while (attempt < MAX_RETRIES) {
            try {
                return callGeminiOnce(prompt);
            } catch (RuntimeException e) {
                boolean is429 = e.getMessage() != null
                    && e.getMessage().contains("429");

                if (is429 && attempt < MAX_RETRIES - 1) {
                    attempt++;
                    log.warn("Gemini 429 — retry {}/{} after {}ms",
                        attempt, MAX_RETRIES, delayMs);
                    Thread.sleep(delayMs);
                    delayMs *= 2; // exponential: 2s → 4s → 8s
                } else {
                    throw e; // non-429 or exhausted retries
                }
            }
        }
        throw new RuntimeException(
            "Gemini unavailable after " + MAX_RETRIES + " retries.");
    }

    private String callGeminiOnce(String prompt) throws Exception {

        // ── Build request with generationConfig for deterministic output ──
        Map<String, Object> requestBody = Map.of(
            "contents", List.of(
                Map.of("parts", List.of(
                    Map.of("text", prompt)
                ))
            ),
            "generationConfig", Map.of(
                "temperature",     0.1,   // low = deterministic JSON
                "maxOutputTokens", 2048,  // cap response size
                "topP",            0.8
            )
        );

        String jsonBody = objectMapper.writeValueAsString(requestBody);

        Request request = new Request.Builder()
            .url(apiUrl + "?key=" + apiKey)
            .post(RequestBody.create(jsonBody, MediaType.parse("application/json")))
            .build();

        try (Response response = httpClient.newCall(request).execute()) {

            if (response.code() == 429) {
                throw new RuntimeException("429");
            }
            if (!response.isSuccessful()) {
                throw new RuntimeException("Gemini API error: " + response.code());
            }

            String responseBody = response.body().string();

            JsonNode root = objectMapper.readTree(responseBody);
            String text = root.path("candidates")
                .get(0)
                .path("content")
                .path("parts")
                .get(0)
                .path("text")
                .asText();

            // Log only first 200 chars to avoid flooding logs
            log.debug("Gemini response preview: {}",
                text.length() > 200 ? text.substring(0, 200) + "…" : text);

            return text;
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  Parse Gemini JSON response → Map
    // ══════════════════════════════════════════════════════════════

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseGeminiResponse(String response) {
        try {
            String cleaned = response
                .replace("```json", "")
                .replace("```", "")
                .trim();

            int jsonStart = cleaned.indexOf("{");
            int jsonEnd   = cleaned.lastIndexOf("}");

            if (jsonStart == -1 || jsonEnd == -1) {
                log.error("No JSON found in Gemini response");
                return Map.of("error", "AI returned invalid response");
            }

            return objectMapper.readValue(
                cleaned.substring(jsonStart, jsonEnd + 1), Map.class);

        } catch (Exception e) {
            log.error("Failed to parse Gemini response: {}", e.getMessage());
            return Map.of("error", "Failed to parse AI response");
        }
    }
}