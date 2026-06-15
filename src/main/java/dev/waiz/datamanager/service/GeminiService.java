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

    public Map<String,Object> analyzeAndSuggestDashboard(List<String> columns,List<Map<String,Object>> sampleRows) throws Exception{

        String prompt = buildPrompt(columns,sampleRows);
        String response = callGemini(prompt);
        return parseGeminiResponse(response);
    }

    private String buildPrompt(List<String> columns,List<Map<String,Object>> sampleRows){
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

        for (int i = 0; i< Math.min(3,sampleRows.size());i++){
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

        // Add this at the very end of buildPrompt before return
        sb.append("\nCRITICAL: Your response must start with { and end with }.");
        sb.append(" Do NOT include any text before or after the JSON.");
        sb.append(" Do NOT use markdown. Do NOT explain anything.\n");

        return sb.toString();
    }

    private String callGemini(String prompt) throws Exception {
        Map<String,Object> requestBody = Map.of(
            "contents",List.of(
                Map.of("parts",List.of(
                    Map.of("text",prompt)
                ))
        )
    );

    String jsonBody = objectMapper.writeValueAsString(requestBody);

    Request request = new Request.Builder()
    .url(apiUrl + "?key="+ apiKey)
    .post(RequestBody.create(jsonBody,
        MediaType.parse("application/json")))
    .build();
    
    try(Response response = httpClient.newCall(request).execute()){

        if (response.code() == 429){
            throw new RuntimeException("AI service is busy.Please wait 1 minute and try again.");
        }
        if (!response.isSuccessful()) {
            throw new RuntimeException("Gemini API error:"+ response.code());
        }
        String responseBody = response.body().string();
        log.info("Gemini response: {}",responseBody);

        JsonNode root = objectMapper.readTree(responseBody);
        String text =  root.path("candidates")
        .get(0)
        .path("content")
        .path("parts")
        .get(0)
        .path("text")
        .asText();

        log.info("Gemini extracted text:{}", text);
        return text;
    }
    }

    @SuppressWarnings("unchecked")
    private Map<String,Object> parseGeminiResponse(String response){
        try{
            String cleaned = response
                             .replace("```json", "")
                             .replace("```","")
                             .trim();

            int jsonStart = cleaned.indexOf("{");
            int jsonEnd = cleaned.lastIndexOf("}");

            if(jsonStart == -1 || jsonEnd == -1){
                log.error("No JSON found in response:{}", cleaned);
                return Map.of("error","AI returned invalid response");
            }
            String jsonOnly = cleaned.substring(jsonStart,jsonEnd + 1);
            return objectMapper.readValue(jsonOnly,Map.class);
        } catch (Exception e){
            log.error("Failed to parse Gemini response:{}",e.getMessage());
            return Map.of("error","Failed to parse AI response");
        }
    }
}
