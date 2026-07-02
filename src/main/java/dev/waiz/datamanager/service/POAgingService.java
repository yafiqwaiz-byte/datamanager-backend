package dev.waiz.datamanager.service;

import dev.waiz.datamanager.dto.POAgingDashboardDTO;
import dev.waiz.datamanager.dto.POAgingReportDTO;
import dev.waiz.datamanager.dto.SubzoneSummaryDTO;
import dev.waiz.datamanager.model.poagingraw;
import dev.waiz.datamanager.model.poagingreport;
import dev.waiz.datamanager.model.fileupload;
import dev.waiz.datamanager.repository.FileUploadRepository;
import dev.waiz.datamanager.repository.POAgingRawRepository;
import dev.waiz.datamanager.repository.POAgingReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class POAgingService {

    private final POAgingReportRepository reportRepository;
    private final POAgingRawRepository    rawRepository;
    private final FileUploadRepository    fileUploadRepository;
    private final POAgingCacheService     cacheService;
    private final GeminiService           geminiService;  // ← ADD THIS

    // ── BA → Subzone mapping (unchanged) ──────────────────────────
    private static final Map<String, String> BA_TO_SUBZONE_MAP = Map.ofEntries(
        Map.entry("6231", "P1"),  Map.entry("6260", "P1"),
        Map.entry("6232", "P2"),  Map.entry("6230", "P2"),  Map.entry("6261", "P2"),
        Map.entry("6290", "SGP/KLM"), Map.entry("6294", "SGP/KLM"),
        Map.entry("6292", "SGP/KLM"), Map.entry("6295", "SGP/KLM"),
        Map.entry("6293", "SGP/KLM"), Map.entry("6244", "SGP/KLM"),
        Map.entry("6240", "ALS/KAN"), Map.entry("6246", "ALS/KAN"),
        Map.entry("6245", "ALS/KAN"), Map.entry("6243", "ALS/KAN"),
        Map.entry("6242", "ALS/KAN"), Map.entry("6201", "ALS/KAN"),
        Map.entry("6210", "A1"),  Map.entry("6219", "A1"),
        Map.entry("6221", "A1"),  Map.entry("6211", "A1"),
        Map.entry("6212", "A1"),  Map.entry("6213", "A1"),
        Map.entry("6227", "A2"),  Map.entry("6250", "A2"),
        Map.entry("6218", "A2"),  Map.entry("6252", "A2"),
        Map.entry("6220", "A3"),  Map.entry("6224", "A3"),
        Map.entry("6223", "A3"),  Map.entry("6222", "A3"),
        Map.entry("6225", "A3")
    );

    private static final Map<String, String> BA_TO_STATION_MAP = Map.ofEntries(
        Map.entry("6231", "TNB SEBERANG JAYA"),   Map.entry("6260", "TNB PULAU PINANG"),
        Map.entry("6232", "TNB NIBONG TEBAL"),     Map.entry("6230", "TNB BERTAM"),
        Map.entry("6261", "TNB BAYAN BARU"),       Map.entry("6290", "TNB SUNGAI PETANI"),
        Map.entry("6294", "TNB KULIM"),            Map.entry("6292", "TNB BALING"),
        Map.entry("6295", "TNB BANDAR BAHARU"),    Map.entry("6293", "TNB SIK"),
        Map.entry("6244", "TNB GUAR CHEMPEDAK"),   Map.entry("6240", "TNB ALOR SETAR"),
        Map.entry("6246", "TNB PENDANG"),          Map.entry("6245", "TNB KUALA NERANG"),
        Map.entry("6243", "TNB LANGKAWI"),         Map.entry("6242", "TNB JITRA"),
        Map.entry("6201", "TNB KANGAR"),           Map.entry("6210", "TNB IPOH"),
        Map.entry("6219", "TNB ULU KINTA"),        Map.entry("6221", "TNB BATU GAJAH"),
        Map.entry("6211", "TNB KAMPAR"),           Map.entry("6212", "TNB BIDOR"),
        Map.entry("6213", "TNB TANJONG MALIM"),    Map.entry("6227", "TNB SRI MANJUNG"),
        Map.entry("6250", "TNB TELUK INTAN"),      Map.entry("6218", "TNB SERI ISKANDAR"),
        Map.entry("6252", "TNB HUTAN MELINTANG"),  Map.entry("6220", "TNB TAIPING"),
        Map.entry("6224", "TNB BAGAN SERAI"),      Map.entry("6223", "TNB GERIK"),
        Map.entry("6222", "TNB KUALA KANGSAR"),    Map.entry("6225", "TNB SG. SIPUT")
    );

    private static final Map<String, String> SUBZONE_LABELS = Map.of(
        "P1","Pulau Pinang 1","P2","Pulau Pinang 2",
        "SGP/KLM","Sungai Petani / Kulim","ALS/KAN","Alor Setar / Kangar",
        "A1","Perak A1","A2","Perak A2","A3","Perak A3"
    );

    private static final List<String> SUBZONE_ORDER =
        List.of("P1","P2","SGP/KLM","ALS/KAN","A1","A2","A3");

    private String getCurrentUsername() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    // ══════════════════════════════════════════════════════════════
    //  STEP 1: Process Raw PO Data
    // ══════════════════════════════════════════════════════════════
    public POAgingDashboardDTO processRawPOData(
            MultipartFile file, UUID uploadId) throws Exception {

        fileupload upload = fileUploadRepository.findById(uploadId)
                .orElseThrow(() -> new RuntimeException("Upload not found: " + uploadId));

        List<Map<String, String>> allRows = readExcelFile(file);
        if (allRows.isEmpty())
            throw new RuntimeException("Excel file is empty or has no data rows.");

        List<Map<String, String>> poOver180 = allRows.stream()
                .filter(row -> {
                    try { return Double.parseDouble(
                        row.getOrDefault("No. of days Outstanding","0")
                           .replace(",","").trim()) > 180;
                    } catch (Exception e) { return false; }
                }).collect(Collectors.toList());

        log.info("Total rows: {}, POs > 180 days: {}", allRows.size(), poOver180.size());

        List<poagingreport> existing = reportRepository.findByUpload_UploadId(uploadId);
        if (!existing.isEmpty()) {
            existing.forEach(r -> rawRepository.deleteAll(
                rawRepository.findByReport_ReportId(r.getReportId())));
            reportRepository.deleteAll(existing);
        }

        Map<String, List<Map<String,String>>> byBusArea = poOver180.stream()
                .collect(Collectors.groupingBy(
                    row -> row.getOrDefault("Bus.Area","Unknown").trim()));

        List<poagingreport> reports = new ArrayList<>();

        for (Map.Entry<String,List<Map<String,String>>> entry : byBusArea.entrySet()) {
            String busArea    = entry.getKey();
            List<Map<String,String>> sr = entry.getValue();
            String stationName = BA_TO_STATION_MAP.getOrDefault(busArea,
                sr.get(0).getOrDefault("Company Name","Unknown"));
            int totalPO = (int) allRows.stream()
                .filter(r -> busArea.equals(r.getOrDefault("Bus.Area","").trim())).count();
            double totalOut = sr.stream()
                .mapToDouble(r -> parseDouble(r.getOrDefault("Outstanding PO value","0"))).sum();
            double pct = totalPO > 0 ? (double) sr.size() / totalPO * 100 : 0;

            reports.add(poagingreport.builder()
                .upload(upload).stationName(stationName).busArea(busArea)
                .subzone(BA_TO_SUBZONE_MAP.getOrDefault(busArea,"Unknown"))
                .countPOOver180(sr.size()).totalPOByStation(totalPO)
                .totalOutstandingValue(totalOut).percentAging(pct)
                .updatedCountPOOver180(sr.size()).updatedOutstandingValue(totalOut)
                .updatedPercentAging(pct).fullyClearedCount(0)
                .partiallyPaidCount(0).totalClearedAmount(0.0).build());
        }

        Set<String> processedBAs = new HashSet<>(byBusArea.keySet());
        BA_TO_STATION_MAP.keySet().forEach(busArea -> {
            if (processedBAs.contains(busArea)) return;
            int totalPO = (int) allRows.stream()
                .filter(r -> busArea.equals(r.getOrDefault("Bus.Area","").trim()))
                .count();
            reports.add(poagingreport.builder()
                .upload(upload)
                .stationName(BA_TO_STATION_MAP.get(busArea))
                .busArea(busArea)
                .subzone(BA_TO_SUBZONE_MAP.getOrDefault(busArea,"Unknown"))
                .countPOOver180(0).totalPOByStation(totalPO)
                .totalOutstandingValue(0.0).percentAging(0.0)
                .updatedCountPOOver180(0).updatedOutstandingValue(0.0)
                .updatedPercentAging(0.0).fullyClearedCount(0)
                .partiallyPaidCount(0).totalClearedAmount(0.0)
                .build());
        });

        List<poagingreport> markedReports = calculateMarks(reports, false);
        List<poagingreport> saved = reportRepository.saveAll(markedReports);
        saveRawPORows(saved, poOver180);

        POAgingDashboardDTO result = buildDashboardDTO(saved);

        // ── Call Gemini ONCE after dashboard is built ──────────────
        // Result stored in DTO → cached → never called again on reload
        result.setAiAnalysis(runGeminiAnalysis(result));

        cacheService.saveCache(uploadId, upload, getCurrentUsername(), result, false);
        log.info("Raw PO dashboard + AI analysis cached for staff: {}", getCurrentUsername());

        return result;
    }

    // ══════════════════════════════════════════════════════════════
    //  STEP 2: Process Cleared PO File
    // ══════════════════════════════════════════════════════════════
    public POAgingDashboardDTO processClearedPOFile(
            MultipartFile file, UUID uploadId) throws Exception {

        List<Map<String,String>> rows = readExcelFile(file);
        log.info("Cleared PO file rows: {}", rows.size());

        Map<String, Double> clearedMap = new LinkedHashMap<>();
        for (Map<String,String> row : rows) {
            String poNo    = row.getOrDefault("PO No.", "").trim();
            String grSAStr = row.getOrDefault("GR/SA Value", "").trim();
            if (poNo.isEmpty() || grSAStr.isEmpty()) continue;
            double grSA = parseDouble(grSAStr);
            if (grSA <= 0) continue;
            if (!clearedMap.containsKey(poNo)) clearedMap.put(poNo, grSA);
        }
        log.info("Unique valid cleared PO entries: {}", clearedMap.size());

        List<poagingreport> reports = reportRepository.findByUpload_UploadId(uploadId);
        if (reports.isEmpty())
            throw new RuntimeException("No reports found for upload: " + uploadId
                + ". Please upload raw PO data first.");

        fileupload upload = reports.get(0).getUpload();

        List<poagingraw> allRawRows = reports.stream()
                .flatMap(r -> rawRepository.findByReport_ReportId(r.getReportId()).stream())
                .collect(Collectors.toList());

        for (poagingraw raw : allRawRows) {
            String poNo = raw.getPoNumber();
            if (clearedMap.containsKey(poNo)) {
                double grSA        = clearedMap.get(poNo);
                double outstanding = raw.getOutstandingPOValue() != null
                                     ? raw.getOutstandingPOValue() : 0.0;
                double remaining   = Math.max(0.0, outstanding - grSA);
                raw.setClearedAmount(grSA);
                raw.setRemainingBalance(remaining);
                raw.setIsCleared(remaining <= 0.0);
                raw.setClearedAt(OffsetDateTime.now());
            } else {
                if (raw.getRemainingBalance() == null) {
                    raw.setRemainingBalance(
                        raw.getOutstandingPOValue() != null ? raw.getOutstandingPOValue() : 0.0);
                }
            }
        }
        rawRepository.saveAll(allRawRows);

        for (poagingreport report : reports) {
            List<poagingraw> reportRows =
                rawRepository.findByReport_ReportId(report.getReportId());

            long stillAging = reportRows.stream()
                    .filter(r -> {
                        double rem = r.getRemainingBalance() != null
                            ? r.getRemainingBalance()
                            : (r.getOutstandingPOValue() != null ? r.getOutstandingPOValue() : 0.0);
                        return rem > 0.0;
                    }).count();

            double remainingTotal = reportRows.stream()
                    .mapToDouble(r -> r.getRemainingBalance() != null
                        ? r.getRemainingBalance()
                        : (r.getOutstandingPOValue() != null ? r.getOutstandingPOValue() : 0.0))
                    .sum();

            double clearedTotal = reportRows.stream()
                    .mapToDouble(r -> r.getClearedAmount() != null ? r.getClearedAmount() : 0.0)
                    .sum();

            int fullyCleared = (int) reportRows.stream()
                    .filter(r -> Boolean.TRUE.equals(r.getIsCleared())).count();

            int partiallyPaid = (int) reportRows.stream()
                    .filter(r -> !Boolean.TRUE.equals(r.getIsCleared())
                              && r.getClearedAmount() != null
                              && r.getClearedAmount() > 0).count();

            double updPct = report.getTotalPOByStation() != null && report.getTotalPOByStation() > 0
                    ? (double) stillAging / report.getTotalPOByStation() * 100 : 0.0;

            report.setUpdatedCountPOOver180((int) stillAging);
            report.setUpdatedOutstandingValue(remainingTotal);
            report.setUpdatedPercentAging(updPct);
            report.setFullyClearedCount(fullyCleared);
            report.setPartiallyPaidCount(partiallyPaid);
            report.setTotalClearedAmount(clearedTotal);
            report.setRemarks(buildRemarks(reportRows.size(), fullyCleared,
                partiallyPaid, remainingTotal));
        }

        reports = calculateMarks(reports, true);
        reportRepository.saveAll(reports);

        POAgingDashboardDTO result = buildDashboardDTO(reports);

        // ── Call Gemini ONCE after updated dashboard is built ──────
        // Cleared file changes the numbers so re-analysis is justified
        result.setAiAnalysis(runGeminiAnalysis(result));

        cacheService.saveCache(uploadId, upload, getCurrentUsername(), result, true);
        log.info("Cleared PO dashboard + AI analysis cached for staff: {}", getCurrentUsername());

        return result;
    }

    // ══════════════════════════════════════════════════════════════
    //  Gemini Analysis — called ONCE per upload, result is cached
    //  Never called on getDashboard, getLatest, or page refresh
    // ══════════════════════════════════════════════════════════════
    private Map<String, Object> runGeminiAnalysis(POAgingDashboardDTO dashboard) {
        try {
            log.info("=== Running Gemini PO Aging Analysis (single call) ===");

            // Build the data map Gemini prompt expects
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("totalPOOver180",           dashboard.getTotalPOOver180());
            data.put("updatedTotalPOOver180",     dashboard.getUpdatedTotalPOOver180());
            data.put("totalOutstandingValue",     dashboard.getTotalOutstandingValue());
            data.put("updatedTotalOutstandingValue", dashboard.getUpdatedTotalOutstandingValue());
            data.put("averagePercentAging",       dashboard.getAveragePercentAging());
            data.put("highAgingStations",         dashboard.getHighAgingStations());
            data.put("mediumAgingStations",       dashboard.getMediumAgingStations());
            data.put("lowAgingStations",          dashboard.getLowAgingStations());
            data.put("totalStations",             dashboard.getTotalStations());
            data.put("totalPOCleared",            dashboard.getTotalPOCleared());
            data.put("totalPOPartiallyPaid",      dashboard.getTotalPOPartiallyPaid());
            data.put("totalClearedAmount",        dashboard.getTotalClearedAmount());
            data.put("percentile33",              dashboard.getPercentile33());
            data.put("percentile66",              dashboard.getPercentile66());
            data.put("stationData",               dashboard.getStationData());
            data.put("subzoneSummary",            dashboard.getSubzoneSummary());

            Map<String, Object> analysis = geminiService.analyzePOAgingDashboard(data);
            log.info("=== Gemini analysis complete ===");
            return analysis;

        } catch (Exception e) {
            // Non-fatal — dashboard still works without AI analysis
            log.warn("Gemini analysis failed (non-fatal, dashboard still returned): {}",
                e.getMessage());
            return Map.of(
                "error",           "AI analysis unavailable",
                "executiveSummary","Analysis could not be generated. Please try again later.",
                "overallStatus",   "Unknown"
            );
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  STEP 3: Get Dashboard by Upload ID (cache-first, no Gemini)
    // ══════════════════════════════════════════════════════════════
    public POAgingDashboardDTO getDashboardByUploadId(UUID uploadId) {
        Optional<POAgingDashboardDTO> cached =
            cacheService.loadCacheByUploadId(uploadId);
        if (cached.isPresent()) {
            log.info("Cache HIT for uploadId: {} — Gemini NOT called", uploadId);
            return cached.get();
        }

        log.info("Cache MISS for uploadId: {} — querying DB (no Gemini call)", uploadId);
        List<poagingreport> reports = reportRepository.findByUpload_UploadId(uploadId);
        if (reports.isEmpty())
            throw new RuntimeException("No reports found for upload: " + uploadId);

        // DB fallback — no Gemini call, aiAnalysis will be null/empty
        // Staff can re-upload to trigger fresh analysis
        return buildDashboardDTO(reports);
    }

    // ══════════════════════════════════════════════════════════════
    //  STEP 4: Get Latest Dashboard (no Gemini)
    // ══════════════════════════════════════════════════════════════
    public POAgingDashboardDTO getLatestDashboard() {
        String username = getCurrentUsername();

        Optional<POAgingDashboardDTO> cached =
            cacheService.loadLatestCache(username);
        if (cached.isPresent()) {
            log.info("Loaded cached dashboard for staff: {} — Gemini NOT called", username);
            return cached.get();
        }

        log.info("No cache for staff: {} — querying DB (no Gemini call)", username);
        List<poagingreport> reports = reportRepository.findLatestReports();
        if (reports.isEmpty())
            throw new RuntimeException("No PO Aging reports found. Please upload data first.");
        return buildDashboardDTO(reports);
    }

    // ══════════════════════════════════════════════════════════════
    //  All remaining methods unchanged
    // ══════════════════════════════════════════════════════════════

    private String buildRemarks(int total, int fullyCleared,
                                int partiallyPaid, double remaining) {
        if (fullyCleared == 0 && partiallyPaid == 0) return null;
        List<String> parts = new ArrayList<>();
        if (fullyCleared  > 0) parts.add(fullyCleared  + " PO" + (fullyCleared  > 1 ? "s" : "") + " fully cleared");
        if (partiallyPaid > 0) parts.add(partiallyPaid + " PO" + (partiallyPaid > 1 ? "s" : "") + " partially paid");
        if (remaining     > 0) parts.add(String.format("RM %.2f remaining", remaining));
        return String.join(" · ", parts);
    }

    private List<poagingreport> calculateMarks(List<poagingreport> reports, boolean useUpdated) {
        List<Double> pcts = reports.stream()
                .filter(r -> {
                    int count = useUpdated
                        ? (r.getUpdatedCountPOOver180() != null ? r.getUpdatedCountPOOver180() : 0)
                        : (r.getCountPOOver180()        != null ? r.getCountPOOver180()        : 0);
                    return count > 0;
                })
                .map(r -> useUpdated ? r.getUpdatedPercentAging() : r.getPercentAging())
                .filter(Objects::nonNull)
                .sorted()
                .collect(Collectors.toList());

        if (pcts.isEmpty()) return reports;

        double p33 = percentile(pcts, 33);
        double p66 = percentile(pcts, 66);
        log.info("Percentile calc — 33rd: {}, 66th: {}", p33, p66);

        for (poagingreport r : reports) {
            double pct = useUpdated
                ? (r.getUpdatedPercentAging() != null ? r.getUpdatedPercentAging() : 0.0)
                : (r.getPercentAging()        != null ? r.getPercentAging()        : 0.0);
            int count = useUpdated
                ? (r.getUpdatedCountPOOver180() != null ? r.getUpdatedCountPOOver180() : 0)
                : (r.getCountPOOver180()        != null ? r.getCountPOOver180()        : 0);

            int mark = count == 0 ? 3
                     : pct <= p33 ? 3
                     : pct <= p66 ? 2
                     : 1;

            if (useUpdated) r.setUpdatedMarks(mark);
            else { r.setMarks(mark); r.setUpdatedMarks(mark); }
        }
        return reports;
    }

    private double percentile(List<Double> sorted, int pct) {
        if (sorted.isEmpty()) return 0;
        int idx = (int) Math.ceil(pct / 100.0 * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(idx, sorted.size() - 1)));
    }

    private POAgingDashboardDTO buildDashboardDTO(List<poagingreport> reports) {
        POAgingDashboardDTO d = new POAgingDashboardDTO();
        d.setTotalPOOver180(reports.stream().mapToInt(r -> r.getCountPOOver180() != null ? r.getCountPOOver180() : 0).sum());
        d.setUpdatedTotalPOOver180(reports.stream().mapToInt(r -> r.getUpdatedCountPOOver180() != null ? r.getUpdatedCountPOOver180() : 0).sum());
        d.setTotalOutstandingValue(reports.stream().mapToDouble(r -> r.getTotalOutstandingValue() != null ? r.getTotalOutstandingValue() : 0.0).sum());
        d.setUpdatedTotalOutstandingValue(reports.stream().mapToDouble(r -> r.getUpdatedOutstandingValue() != null ? r.getUpdatedOutstandingValue() : 0.0).sum());
        d.setAveragePercentAging(reports.stream().mapToDouble(r -> r.getPercentAging() != null ? r.getPercentAging() : 0.0).average().orElse(0.0));
        d.setUpdatedAveragePercentAging(reports.stream().mapToDouble(r -> r.getUpdatedPercentAging() != null ? r.getUpdatedPercentAging() : 0.0).average().orElse(0.0));
        d.setHighAgingStations((int) reports.stream().filter(r -> Integer.valueOf(1).equals(r.getUpdatedMarks())).count());
        d.setMediumAgingStations((int) reports.stream().filter(r -> Integer.valueOf(2).equals(r.getUpdatedMarks())).count());
        d.setLowAgingStations((int) reports.stream().filter(r -> Integer.valueOf(3).equals(r.getUpdatedMarks())).count());
        d.setTotalStations(reports.size());
        d.setTotalPOCleared(reports.stream().mapToInt(r -> r.getFullyClearedCount() != null ? r.getFullyClearedCount() : 0).sum());
        d.setTotalPOPartiallyPaid(reports.stream().mapToInt(r -> r.getPartiallyPaidCount() != null ? r.getPartiallyPaidCount() : 0).sum());
        d.setTotalClearedAmount(reports.stream().mapToDouble(r -> r.getTotalClearedAmount() != null ? r.getTotalClearedAmount() : 0.0).sum());
        List<Double> pcts = reports.stream().map(p -> Objects.requireNonNull(p).getPercentAging()).filter(Objects::nonNull).sorted().collect(Collectors.toList());
        d.setPercentile33(percentile(pcts, 33));
        d.setPercentile66(percentile(pcts, 66));
        d.setMarkDistribution(Map.of("High Aging (1)", d.getHighAgingStations(), "Medium Aging (2)", d.getMediumAgingStations(), "Low Aging (3)", d.getLowAgingStations()));
        d.setStationData(reports.stream().map(this::toDTO).sorted(Comparator.comparingInt((POAgingReportDTO r) -> r.getUpdatedCountPOOver180() != null ? r.getUpdatedCountPOOver180() : 0).reversed()).collect(Collectors.toList()));
        d.setSubzoneSummary(buildSubzoneSummary(reports));
        // Note: aiAnalysis NOT set here — only set in processRaw and processCleared
        // This ensures Gemini is never called from getDashboard or getLatest
        return d;
    }

    private List<SubzoneSummaryDTO> buildSubzoneSummary(List<poagingreport> reports) {
        return reports.stream()
                .collect(Collectors.groupingBy(r -> r.getSubzone() != null ? r.getSubzone() : "Unknown"))
                .entrySet().stream().map(entry -> {
                    String subzone = entry.getKey();
                    List<poagingreport> sr = entry.getValue();
                    SubzoneSummaryDTO dto = new SubzoneSummaryDTO();
                    dto.setSubzone(subzone);
                    dto.setSubzoneLabel(SUBZONE_LABELS.getOrDefault(subzone, subzone));
                    dto.setTotalStations(sr.size());
                    dto.setTotalPOOver180(sr.stream().mapToInt(r -> r.getCountPOOver180() != null ? r.getCountPOOver180() : 0).sum());
                    dto.setUpdatedTotalPOOver180(sr.stream().mapToInt(r -> r.getUpdatedCountPOOver180() != null ? r.getUpdatedCountPOOver180() : 0).sum());
                    dto.setTotalOutstandingValue(sr.stream().mapToDouble(r -> r.getTotalOutstandingValue() != null ? r.getTotalOutstandingValue() : 0.0).sum());
                    dto.setUpdatedOutstandingValue(sr.stream().mapToDouble(r -> r.getUpdatedOutstandingValue() != null ? r.getUpdatedOutstandingValue() : 0.0).sum());
                    double avgMark = sr.stream().mapToInt(r -> r.getUpdatedMarks() != null ? r.getUpdatedMarks() : 3).average().orElse(3.0);
                    int avgMarkRounded = Math.max(1, Math.min(3, (int) Math.round(avgMark)));
                    dto.setMarks(avgMarkRounded);
                    dto.setHighAgingCount((int) sr.stream().filter(r -> Integer.valueOf(1).equals(r.getUpdatedMarks())).count());
                    dto.setMediumAgingCount((int) sr.stream().filter(r -> Integer.valueOf(2).equals(r.getUpdatedMarks())).count());
                    dto.setLowAgingCount((int) sr.stream().filter(r -> Integer.valueOf(3).equals(r.getUpdatedMarks())).count());
                    return dto;
                })
                .sorted(Comparator.comparingInt(dto -> { int i = SUBZONE_ORDER.indexOf(dto.getSubzone()); return i == -1 ? Integer.MAX_VALUE : i; }))
                .collect(Collectors.toList());
    }

    public void updateCachedAnalysis(UUID uploadId, Map<String, Object> analysis) {
    cacheService.loadCacheByUploadId(uploadId).ifPresent(dto -> {
    dto.setAiAnalysis(analysis);
    fileUploadRepository.findById(uploadId).ifPresent(upload ->
    cacheService.saveCache(uploadId, upload, getCurrentUsername(), dto, true)
            );
        });
    }

    private void saveRawPORows(List<poagingreport> reports, List<Map<String,String>> poOver180) {
        Map<String, poagingreport> reportMap = reports.stream()
                .collect(Collectors.toMap(p -> Objects.requireNonNull(p).getBusArea(), r -> r, (a,b) -> a));
        List<poagingraw> rawRows = new ArrayList<>();
        for (Map<String,String> row : poOver180) {
            String busArea = row.getOrDefault("Bus.Area","").trim();
            poagingreport report = reportMap.get(busArea);
            if (report == null) { log.warn("No report for Bus.Area: {}", busArea); continue; }
            double outstanding = parseDouble(row.getOrDefault("Outstanding PO value","0"));
            rawRows.add(poagingraw.builder()
                .report(report)
                .poNumber(row.getOrDefault("PO No.",""))
                .poItem(row.getOrDefault("PO Item",""))
                .poDescription(row.getOrDefault("PO Description",""))
                .busArea(busArea)
                .companyCode(row.getOrDefault("Company Code",""))
                .companyName(row.getOrDefault("Company Name",""))
                .vendorAccNo(row.getOrDefault("Vendor Acc.No.",""))
                .vendorName(row.getOrDefault("Name",""))
                .createdDate(row.getOrDefault("Created Date",""))
                .itemDeliveryDate(row.getOrDefault("Item Delivery Date",""))
                .validityStart(row.getOrDefault("Validity Start",""))
                .validityEnd(row.getOrDefault("Validity End",""))
                .requisitioner(row.getOrDefault("Requisitioner",""))
                .requisitionerName(row.getOrDefault("Requisitioner Name",""))
                .requisitionerEmail(row.getOrDefault("Requisitioner Email",""))
                .requisitionerDepartment(row.getOrDefault("Requisitioner Department",""))
                .requisitionerDivision(row.getOrDefault("Requisitioner Division",""))
                .prCreator(row.getOrDefault("PR Creator",""))
                .prCreatorName(row.getOrDefault("PR Creator Name",""))
                .prCreatorEmail(row.getOrDefault("PR Creator Email",""))
                .prCreatorDepartment(row.getOrDefault("PR Creator Department",""))
                .prCreatorDivision(row.getOrDefault("PR Creator Division",""))
                .outlineAgreementNo(row.getOrDefault("Outline Agreement No.",""))
                .outlineAgreementItem(row.getOrDefault("Outline Agreement Item",""))
                .outlineAgreementDescription(row.getOrDefault("Outline Agreement Description",""))
                .netOrderValue(parseDouble(row.getOrDefault("Net Order Value","0")))
                .currency(row.getOrDefault("Curr",""))
                .exchangeRate(parseDouble(row.getOrDefault("Exchange rate","0")))
                .outstandingPOValue(outstanding)
                .remainingBalance(outstanding)
                .trackingNo(row.getOrDefault("Tracking No.",""))
                .heldPO(row.getOrDefault("Held PO",""))
                .noOfDaysOutstanding(parseInt(row.getOrDefault("No. of days Outstanding","0")))
                .isCleared(false)
                .clearedAmount(0.0)
                .build());
        }
        rawRepository.saveAll(rawRows);
        log.info("Saved {} raw PO rows", rawRows.size());
    }

    private List<Map<String,String>> readExcelFile(MultipartFile file) throws Exception {
        List<Map<String,String>> rows = new ArrayList<>();
        try (Workbook wb = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = wb.getSheetAt(0);
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) throw new RuntimeException("No header row found.");
            List<String> headers = new ArrayList<>();
            for (Cell c : headerRow) headers.add(c.getStringCellValue().trim());
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                boolean empty = true;
                for (int j = 0; j < headers.size(); j++) {
                    Cell c = row.getCell(j, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                    if (c != null && c.getCellType() != CellType.BLANK) { empty = false; break; }
                }
                if (empty) continue;
                Map<String,String> rd = new LinkedHashMap<>();
                for (int j = 0; j < headers.size(); j++)
                    rd.put(headers.get(j), getCellValue(row.getCell(j, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK)));
                rows.add(rd);
            }
        }
        log.info("Read {} data rows from Excel", rows.size());
        return rows;
    }

    private POAgingReportDTO toDTO(poagingreport r) {
        POAgingReportDTO dto = new POAgingReportDTO();
        dto.setReportId(r.getReportId());
        dto.setStationName(r.getStationName());
        dto.setBusArea(r.getBusArea());
        dto.setSubzone(r.getSubzone());
        dto.setSubzoneLabel(SUBZONE_LABELS.getOrDefault(r.getSubzone(), r.getSubzone()));
        dto.setCountPOOver180(r.getCountPOOver180());
        dto.setTotalPOByStation(r.getTotalPOByStation());
        dto.setTotalOutstandingValue(r.getTotalOutstandingValue());
        dto.setPercentAging(r.getPercentAging());
        dto.setMarks(r.getMarks());
        dto.setUpdatedCountPOOver180(r.getUpdatedCountPOOver180());
        dto.setUpdatedOutstandingValue(r.getUpdatedOutstandingValue());
        dto.setUpdatedPercentAging(r.getUpdatedPercentAging());
        dto.setUpdatedMarks(r.getUpdatedMarks());
        dto.setFullyClearedCount(r.getFullyClearedCount());
        dto.setPartiallyPaidCount(r.getPartiallyPaidCount());
        dto.setTotalClearedAmount(r.getTotalClearedAmount());
        dto.setRemarks(r.getRemarks());
        return dto;
    }

    private String getCellValue(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell))
                    yield cell.getLocalDateTimeCellValue().toLocalDate().toString();
                double v = cell.getNumericCellValue();
                yield v % 1 == 0 ? String.valueOf((long) v) : String.valueOf(v);
            }
            case STRING  -> cell.getStringCellValue().trim();
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try { double v = cell.getNumericCellValue();
                    yield v % 1 == 0 ? String.valueOf((long) v) : String.valueOf(v);
                } catch (Exception e) { yield cell.getStringCellValue().trim(); }
            }
            default -> "";
        };
    }

    private Double parseDouble(String v) {
        if (v == null || v.isBlank()) return 0.0;
        try { return Double.parseDouble(v.replace(",","").replace("RM","").trim()); }
        catch (Exception e) { return 0.0; }
    }

    private Integer parseInt(String v) {
        if (v == null || v.isBlank()) return 0;
        try { return (int) Double.parseDouble(v.replace(",","").trim()); }
        catch (Exception e) { return 0; }
    }
}