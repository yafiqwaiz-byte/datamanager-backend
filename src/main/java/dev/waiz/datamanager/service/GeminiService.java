package dev.waiz.datamanager.service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

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
    private final OkHttpClient httpClient = new OkHttpClient();

    // ══════════════════════════════════════════════════════════════
    //  PO Aging Dashboard Analysis
    // ══════════════════════════════════════════════════════════════

    public Map<String, Object> analyzePOAgingDashboard(
            Map<String, Object> dashboardData) throws Exception {

        String prompt = buildPOAgingPrompt(dashboardData);
        String response = callGemini(prompt);
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
        sb.append("  \"keyRisks\": [\n");
        sb.append("    \"risk 1\",\n");
        sb.append("    \"risk 2\",\n");
        sb.append("    \"risk 3\"\n");
        sb.append("  ],\n");
        sb.append("  \"positiveObservations\": [\n");
        sb.append("    \"positive finding 1\",\n");
        sb.append("    \"positive finding 2\"\n");
        sb.append("  ],\n");
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
    //  NEW — Pass 0: Structured document extraction
    //  Called BEFORE string matching to extract a clean JSON
    //  representation of the document (type, sender, recipient,
    //  dates, details, etc.) from raw OCR text.
    // ══════════════════════════════════════════════════════════════

    public Map<String, Object> extractDocumentStructure(String ocrText) throws Exception {

        String prompt = buildStructuredExtractionPrompt(ocrText);
        String rawResponse = callGemini(prompt);

        try {
            String cleaned = rawResponse
                .replace("```json", "")
                .replace("```", "")
                .trim();

            int start = cleaned.indexOf("{");
            int end   = cleaned.lastIndexOf("}");
            if (start == -1 || end == -1) {
                log.warn("Gemini structured extraction returned no JSON — raw: {}", cleaned);
                return Collections.emptyMap();
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> result = objectMapper.readValue(
                cleaned.substring(start, end + 1), Map.class);

            log.info("Pass 0 structured extraction — documentType: {}",
                result.getOrDefault("documentType", "unknown"));

            return result;

        } catch (Exception e) {
            log.warn("Failed to parse structured extraction response: {}", e.getMessage());
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
        sb.append("    \"name\": \"\",\n");
        sb.append("    \"staffId\": \"\",\n");
        sb.append("    \"company\": \"\",\n");
        sb.append("    \"position\": \"\",\n");
        sb.append("    \"department\": \"\",\n");
        sb.append("    \"email\": \"\",\n");
        sb.append("    \"phone\": \"\"\n");
        sb.append("  },\n");
        sb.append("  \"recipient\": {\n");
        sb.append("    \"name\": \"\",\n");
        sb.append("    \"position\": \"\",\n");
        sb.append("    \"department\": \"\",\n");
        sb.append("    \"company\": \"\"\n");
        sb.append("  },\n");
        sb.append("  \"subject\": \"\",\n");
        sb.append("  \"dates\": {\n");
        sb.append("    \"documentDate\": \"\",\n");
        sb.append("    \"startDate\": \"\",\n");
        sb.append("    \"endDate\": \"\",\n");
        sb.append("    \"effectiveDate\": \"\"\n");
        sb.append("  },\n");
        sb.append("  \"referenceNumber\": \"\",\n");
        sb.append("  \"purpose\": \"\",\n");
        sb.append("  \"details\": {\n");
        sb.append("    \"leaveType\": \"\",\n");
        sb.append("    \"position\": \"\",\n");
        sb.append("    \"salary\": \"\",\n");
        sb.append("    \"location\": \"\",\n");
        sb.append("    \"duration\": \"\"\n");
        sb.append("  },\n");
        sb.append("  \"attachments\": [],\n");
        sb.append("  \"remarks\": \"\"\n");
        sb.append("}\n\n");

        sb.append("RULES:\n");
        sb.append("1. Detect document language (Malay/English/mixed) automatically.\n");
        sb.append("2. Common Malay field mappings:\n");
        sb.append("   Tarikh = date, Nama = name, Jawatan = position,\n");
        sb.append("   Syarikat = company, Alamat = address,\n");
        sb.append("   Dari/Daripada = sender, Kepada = recipient,\n");
        sb.append("   Perkara/Perihal = subject, Rujukan = reference number,\n");
        sb.append("   Bahagian/Jabatan = department, Gaji = salary.\n");
        sb.append("3. Leave empty string \"\" for fields not found — do NOT invent values.\n");
        sb.append("4. Dates: return in YYYY-MM-DD format if possible.\n");
        sb.append("5. Return only the JSON object, starting with { and ending with }.\n\n");

        sb.append("OCR TEXT:\n");
        sb.append("---START---\n");
        sb.append(ocrText);
        sb.append("\n---END---\n");

        return sb.toString();
    }

    // ══════════════════════════════════════════════════════════════
    //  OCR → Placeholder Mapping  (Pass 2 fallback)
    //  Called by FieldMappingService for placeholders that Pass 0
    //  and Pass 1 string-matching could not resolve.
    // ══════════════════════════════════════════════════════════════

    @SuppressWarnings("unchecked")
    public Map<String, String> mapOcrToPlaceholders(
            String ocrText,
            List<String> unmappedPhs,
            Map<String, String> partialResults) throws Exception {

        String prompt = buildOcrMappingPrompt(ocrText, unmappedPhs, partialResults);
        String rawResponse = callGemini(prompt);

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
        sb.append("You specialize in Malay/English mixed documents including letters, memos, forms, and HR documents.\n\n");

        // ── Already resolved context ──────────────────────────────
        sb.append("CONTEXT — fields already successfully extracted (DO NOT re-map these):\n");
        if (partialResults.isEmpty()) {
            sb.append("  (nothing extracted yet)\n");
        } else {
            partialResults.forEach((k, v) ->
                sb.append("  ").append(k).append(" = \"").append(v).append("\"\n"));
        }
        sb.append("\n");

        // ── Full OCR text ─────────────────────────────────────────
        sb.append("FULL OCR TEXT (may contain noise, line breaks, Malay/English mix):\n");
        sb.append("---START---\n");
        sb.append(ocrText);
        sb.append("\n---END---\n\n");

        // ── Placeholders to resolve ───────────────────────────────
        sb.append("UNRESOLVED PLACEHOLDERS (you must attempt to find a value for each):\n");
        for (String ph : unmappedPlaceholders) {
            String hint = ph.replace("[", "").replace("]", "").replace("_", " ").toLowerCase();
            String malayHint = getMalayHint(hint);
            sb.append("  ").append(ph)
              .append("  (meaning: ").append(hint)
              .append(malayHint.isEmpty() ? "" : " | Malay equivalent: " + malayHint)
              .append(")\n");
        }
        sb.append("\n");

        // ── Rules ─────────────────────────────────────────────────
        sb.append("EXTRACTION RULES:\n");
        sb.append("1. Extract values ONLY from the OCR text — do NOT invent or assume values.\n");
        sb.append("2. If a field cannot be found, return empty string \"\" — never null.\n");
        sb.append("3. Do NOT re-map placeholders already listed in CONTEXT above.\n");
        sb.append("4. Handle OCR noise: 'Nam4'→'Nama', 'Tanikh'→'Tarikh', '0'→'O' in names, etc.\n");
        sb.append("5. Dates: return in the exact format found in the document (e.g. '15 Januari 2026').\n");
        sb.append("6. Names: include full name as written, preserve 'bin'/'binti'/'a/l'/'a/p'.\n");
        sb.append("7. Reference numbers: preserve original format (e.g. 'TNB/AD/2026/001').\n\n");

        sb.append("MALAY FIELD ABBREVIATIONS (use these to locate values in OCR text):\n");
        sb.append("  Tarikh / Tkh         = date\n");
        sb.append("  Nama                 = name\n");
        sb.append("  Jawatan              = position / title\n");
        sb.append("  Bahagian / Jabatan   = department / division\n");
        sb.append("  Syarikat             = company\n");
        sb.append("  Alamat               = address\n");
        sb.append("  No. Tel / Tel        = phone number\n");
        sb.append("  Faks                 = fax\n");
        sb.append("  Bil. / No. Rujukan   = reference number\n");
        sb.append("  Kepada               = recipient (to)\n");
        sb.append("  Daripada / Dari      = sender (from)\n");
        sb.append("  Perkara / Perihal    = subject / regarding\n");
        sb.append("  Gaji / Emolumen      = salary\n");
        sb.append("  Cuti                 = leave\n");
        sb.append("  Tempoh               = duration / period\n");
        sb.append("  Mulai / Bermula      = start date\n");
        sb.append("  Hingga / Sehingga    = end date\n\n");

        // ── Output format ─────────────────────────────────────────
        sb.append("RESPOND ONLY with a valid JSON object. No markdown, no explanation.\n");
        sb.append("Every placeholder listed above MUST appear as a key in the response.\n");
        sb.append("Example:\n");
        sb.append("{\n");
        sb.append("  \"[TARIKH]\": \"15 Januari 2026\",\n");
        sb.append("  \"[NAMA_PEKERJA]\": \"Ahmad bin Ali\",\n");
        sb.append("  \"[JAWATAN]\": \"Jurutera\",\n");
        sb.append("  \"[NOMBOR_RUJUKAN]\": \"\"\n");
        sb.append("}\n");
        sb.append("CRITICAL: Start with { and end with }. No text before or after.\n");

        return sb.toString();
    }

    // ── Malay hint lookup for common placeholder names ────────────
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
    //  Shared: call Gemini API
    // ══════════════════════════════════════════════════════════════

    private String callGemini(String prompt) throws Exception {
        Map<String, Object> requestBody = Map.of(
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
                throw new RuntimeException(
                    "AI service is busy. Please wait 1 minute and try again.");
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

    // ══════════════════════════════════════════════════════════════
    //  Shared: parse Gemini JSON response
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
                log.error("No JSON found in Gemini response: {}", cleaned);
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