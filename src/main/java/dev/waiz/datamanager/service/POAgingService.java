package dev.waiz.datamanager.service;

import dev.waiz.datamanager.dto.DuplicatePODTO;
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
    private final GeminiService           geminiService;

    // ── BA → Subzone mapping ───────────────────────────────────────
    private static final Map<String, String> BA_TO_SUBZONE_MAP = Map.ofEntries(
        Map.entry("6260", "P1"),  Map.entry("6261", "P1"),
        Map.entry("6231", "P2"),  Map.entry("6230", "P2"),  Map.entry("6232", "P2"),
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
                try {
                    return Double.parseDouble(
                        row.getOrDefault("No. of days Outstanding","0")
                           .replace(",","").trim()) > 180;
                } catch (Exception e) { return false; }
            }).collect(Collectors.toList());

    log.info("Total rows: {}, POs > 180 days: {}", allRows.size(), poOver180.size());

    // NEW — capture previous %Aging per Bus.Area BEFORE deleting old reports,
    // using the most recent prior upload's data (not this uploadId, since
    // it's a fresh upload each time).
    Map<String, Double> previousPercentByBA = reportRepository.findLatestReports().stream()
            .filter(r -> r.getUpdatedPercentAging() != null)
            .collect(Collectors.toMap(
                r -> r.getBusArea(),
                r -> r.getUpdatedPercentAging(),
                (a, b) -> a));   // keep first (most recent) if duplicates

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
        String busArea     = entry.getKey();
        List<Map<String,String>> sr = entry.getValue();
        String stationName = BA_TO_STATION_MAP.getOrDefault(busArea,
            sr.get(0).getOrDefault("Company Name","Unknown"));

        int totalPO = (int) allRows.stream()
            .filter(r -> busArea.equals(r.getOrDefault("Bus.Area","").trim())).count();

        double totalOut = sr.stream()
            .mapToDouble(r -> parseDouble(
                r.getOrDefault("Outstanding PO value","0"))).sum();

        double pct = totalPO > 0 ? (double) sr.size() / totalPO * 100 : 0;

        reports.add(poagingreport.builder()
            .upload(upload).stationName(stationName).busArea(busArea)
            .subzone(BA_TO_SUBZONE_MAP.getOrDefault(busArea,"Unknown"))
            .countPOOver180(sr.size()).totalPOByStation(totalPO)
            .totalOutstandingValue(totalOut).percentAging(pct)
            .updatedCountPOOver180(sr.size()).updatedOutstandingValue(totalOut)
            .updatedPercentAging(pct)
            .previousPercentAging(previousPercentByBA.get(busArea))   // NEW
            .fullyClearedCount(0)
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
            .updatedPercentAging(0.0)
            .previousPercentAging(previousPercentByBA.get(busArea))   // NEW
            .fullyClearedCount(0)
            .partiallyPaidCount(0).totalClearedAmount(0.0)
            .build());
    });

    List<poagingreport> markedReports = calculateMarks(reports, false);
    List<poagingreport> saved         = reportRepository.saveAll(markedReports);
    saveRawPORows(saved, poOver180);

    POAgingDashboardDTO result = buildDashboardDTO(saved);
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
    Map<String, Integer> poOccurrenceCount = new LinkedHashMap<>();   // NEW

    for (Map<String,String> row : rows) {
        String poNo     = row.getOrDefault("PO No.", "").trim();
        String grSAStr  = row.getOrDefault("GR/SA Value", "").trim();
        String poValStr = row.getOrDefault("PO Value", "").trim();

        if (poNo.isEmpty()) continue;

        poOccurrenceCount.merge(poNo, 1, (existing,increment)->existing + increment);   // NEW

        double grSA  = parseDouble(grSAStr);
        double poVal = parseDouble(poValStr);

        Double clearedAmount = null;

        if (!grSAStr.isEmpty() && grSA > 0) {
            clearedAmount = grSA;
        } else if (!poValStr.isEmpty() && poVal > 0) {
            clearedAmount = poVal;
        }

        if (clearedAmount != null && !clearedMap.containsKey(poNo)) {
            clearedMap.put(poNo, clearedAmount);
        }
    }
    log.info("Unique valid cleared PO entries: {}", clearedMap.size());

    // NEW — collect PO numbers seen more than once in this cleared file
    List<String> duplicatePONumbers = poOccurrenceCount.entrySet().stream()
            .filter(e -> e.getValue() > 1)
            .map(e -> e.getKey())
            .collect(Collectors.toList());
    log.info("Duplicate PO numbers found in cleared file: {}", duplicatePONumbers.size());

    List<poagingreport> reports = reportRepository.findByUpload_UploadId(uploadId);
    if (reports.isEmpty())
        throw new RuntimeException("No reports found for upload: " + uploadId
            + ". Please upload raw PO data first.");

    fileupload upload = reports.get(0).getUpload();

    List<poagingraw> allRawRows = reports.stream()
            .flatMap(r -> rawRepository.findByReport_ReportId(r.getReportId()).stream())
            .collect(Collectors.toList());

    // NEW — map duplicate PO numbers to their subzone/station via raw rows
    Map<String, poagingraw> rawByPoNo = allRawRows.stream()
    .collect(Collectors.toMap(r -> r.getPoNumber(), r -> r, (a, b) -> a));

    List<DuplicatePODTO> duplicatePODTOs = new ArrayList<>();
    for (String dupPoNo : duplicatePONumbers) {
        poagingraw matched = rawByPoNo.get(dupPoNo);
        if (matched == null) continue;   // PO not part of this upload's >180 raw data

        poagingreport rep = matched.getReport();
        duplicatePODTOs.add(new DuplicatePODTO(
            dupPoNo,
            poOccurrenceCount.get(dupPoNo),
            clearedMap.getOrDefault(dupPoNo, 0.0),
            rep != null ? rep.getSubzone()     : "Unknown",
            rep != null ? rep.getBusArea()     : "",
            rep != null ? rep.getStationName() : ""
        ));
    }

    // ── (unchanged) existing clearing logic ──────────────────────
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
                    raw.getOutstandingPOValue() != null
                        ? raw.getOutstandingPOValue() : 0.0);
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
                        : (r.getOutstandingPOValue() != null
                            ? r.getOutstandingPOValue() : 0.0);
                    return rem > 0.0;
                }).count();

        double remainingTotal = reportRows.stream()
                .mapToDouble(r -> r.getRemainingBalance() != null
                    ? r.getRemainingBalance()
                    : (r.getOutstandingPOValue() != null
                        ? r.getOutstandingPOValue() : 0.0))
                .sum();

        double clearedTotal = reportRows.stream()
                .mapToDouble(r -> r.getClearedAmount() != null
                    ? r.getClearedAmount() : 0.0)
                .sum();

        int fullyCleared = (int) reportRows.stream()
                .filter(r -> Boolean.TRUE.equals(r.getIsCleared())).count();

        int partiallyPaid = (int) reportRows.stream()
                .filter(r -> !Boolean.TRUE.equals(r.getIsCleared())
                          && r.getClearedAmount() != null
                          && r.getClearedAmount() > 0).count();

        double updPct = report.getTotalPOByStation() != null
                && report.getTotalPOByStation() > 0
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

    // NEW — attach duplicate PO info to each subzone summary
    Map<String, List<DuplicatePODTO>> dupsBySubzone = duplicatePODTOs.stream()
    .collect(Collectors.groupingBy(d -> d.getSubzone()));
    result.getSubzoneSummary().forEach(sz ->
        sz.setDuplicatePOs(dupsBySubzone.getOrDefault(sz.getSubzone(), new ArrayList<>())));

    result.setAiAnalysis(runGeminiAnalysis(result));
    cacheService.saveCache(uploadId, upload, getCurrentUsername(), result, true);
    log.info("Cleared PO dashboard + AI analysis cached: {}", getCurrentUsername());
    return result;
}

    // ══════════════════════════════════════════════════════════════
    //  Gemini Analysis — called ONCE per upload, result is cached
    // ══════════════════════════════════════════════════════════════
    private Map<String, Object> runGeminiAnalysis(POAgingDashboardDTO dashboard) {
        try {
            log.info("=== Running Gemini PO Aging Analysis (single call) ===");
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("totalPOOver180",              dashboard.getTotalPOOver180());
            data.put("updatedTotalPOOver180",        dashboard.getUpdatedTotalPOOver180());
            data.put("totalOutstandingValue",        dashboard.getTotalOutstandingValue());
            data.put("updatedTotalOutstandingValue", dashboard.getUpdatedTotalOutstandingValue());
            data.put("averagePercentAging",          dashboard.getAveragePercentAging());
            data.put("highAgingStations",            dashboard.getHighAgingStations());
            data.put("mediumAgingStations",          dashboard.getMediumAgingStations());
            data.put("lowAgingStations",             dashboard.getLowAgingStations());
            data.put("totalStations",                dashboard.getTotalStations());
            data.put("totalPOCleared",               dashboard.getTotalPOCleared());
            data.put("totalPOPartiallyPaid",         dashboard.getTotalPOPartiallyPaid());
            data.put("totalClearedAmount",           dashboard.getTotalClearedAmount());
            data.put("percentile33",                 dashboard.getPercentile33());
            data.put("percentile66",                 dashboard.getPercentile66());
            data.put("stationData",                  dashboard.getStationData());
            data.put("subzoneSummary",               dashboard.getSubzoneSummary());
            Map<String, Object> analysis = geminiService.analyzePOAgingDashboard(data);
            log.info("=== Gemini analysis complete ===");
            return analysis;
        } catch (Exception e) {
            log.warn("Gemini analysis failed (non-fatal): {}", e.getMessage());
            return Map.of(
                "error",           "AI analysis unavailable",
                "executiveSummary","Analysis could not be generated. Please try again later.",
                "overallStatus",   "Unknown"
            );
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  STEP 3: Get Dashboard (cache-first, no Gemini)
    // ══════════════════════════════════════════════════════════════
    public POAgingDashboardDTO getDashboardByUploadId(UUID uploadId) {
        Optional<POAgingDashboardDTO> cached = cacheService.loadCacheByUploadId(uploadId);
        if (cached.isPresent()) {
            log.info("Cache HIT for uploadId: {} — Gemini NOT called", uploadId);
            return cached.get();
        }
        log.info("Cache MISS for uploadId: {} — querying DB", uploadId);
        List<poagingreport> reports = reportRepository.findByUpload_UploadId(uploadId);
        if (reports.isEmpty())
            throw new RuntimeException("No reports found for upload: " + uploadId);
        return buildDashboardDTO(reports);
    }

    // ══════════════════════════════════════════════════════════════
    //  STEP 4: Get Latest Dashboard (no Gemini)
    // ══════════════════════════════════════════════════════════════
    public POAgingDashboardDTO getLatestDashboard() {
        String username = getCurrentUsername();
        Optional<POAgingDashboardDTO> cached = cacheService.loadLatestCache(username);
        if (cached.isPresent()) {
            log.info("Loaded cached dashboard for staff: {} — Gemini NOT called", username);
            return cached.get();
        }
        log.info("No cache for staff: {} — querying DB", username);
        List<poagingreport> reports = reportRepository.findLatestReports();
        if (reports.isEmpty())
            throw new RuntimeException("No PO Aging reports found. Please upload data first.");
        return buildDashboardDTO(reports);
    }

    // ══════════════════════════════════════════════════════════════
    //  Mark calculation -- NOW SIZE-BRACKETED
    //
    //  ✅ Formula 1 (Percentile):
    //  =PERCENTILE(IF(F2:F33>0, D2:D33), 0.33)
    //  → Only stations WITH aging POs (count > 0) are included
    //  → Stations with 0 PO > 180 auto-get Mark 3, excluded from calc
    //
    //  ✅ Formula 2 (Marks):
    //  =IF(E2=0, 3, IF(H2<=P33, 3, IF(H2<=P66, 2, 1)))
    //  → 0 POs = Mark 3
    //  → %Aging ≤ 33rd pct = Mark 3
    //  → %Aging ≤ 66th pct = Mark 2
    //  → %Aging > 66th pct = Mark 1
    //  Stations are grouped into Large/Medium/Small by totalPOByStation
    //  (tertiles of the current dataset), then percentile p33/p66 is
    //  computed SEPARATELY within each bracket. This prevents large-
    //  volume stations from being structurally disadvantaged, since
    //  their %Aging naturally moves slower than small stations' does.
    // ══════════════════════════════════════════════════════════════
    private List<poagingreport> calculateMarks(List<poagingreport> reports, boolean useUpdated) {
         // Step 1 — assign size bracket based on totalPOByStation tertiles
    List<Integer> totals = reports.stream()
            .map(r -> r.getTotalPOByStation() != null ? r.getTotalPOByStation() : 0)
            .filter(t -> t != null && t > 0)
            .sorted()
            .collect(Collectors.toList());

    if (totals.isEmpty()) return reports;

    double sizeP33 = percentile(totals.stream().map(t -> t.doubleValue())
            .collect(Collectors.toList()), 33);
    double sizeP66 = percentile(totals.stream().map(t -> t.doubleValue())
            .collect(Collectors.toList()), 66);

    for (poagingreport r : reports) {
        int totalPO = r.getTotalPOByStation() != null ? r.getTotalPOByStation() : 0;
        String bracket = totalPO <= sizeP33 ? "Small"
                        : totalPO <= sizeP66 ? "Medium"
                        : "Large";
        r.setSizeBracket(bracket);
    }

    // Step 2 — compute percentile p33/p66 WITHIN each bracket separately
    Map<String, List<Double>> pctsByBracket = reports.stream()
            .filter(r -> r.getTotalPOByStation() != null && r.getTotalPOByStation() > 0)
            .collect(Collectors.groupingBy(
                r -> r.getSizeBracket() != null ? r.getSizeBracket() : "Medium",
                Collectors.mapping(
                    r -> useUpdated ? r.getUpdatedPercentAging() : r.getPercentAging(),
                    Collectors.filtering(Objects::nonNull, Collectors.toList()))));

    Map<String, Double> p33ByBracket = new HashMap<>();
    Map<String, Double> p66ByBracket = new HashMap<>();
    for (Map.Entry<String, List<Double>> e : pctsByBracket.entrySet()) {
        List<Double> sorted = e.getValue().stream().sorted().collect(Collectors.toList());
        p33ByBracket.put(e.getKey(), percentile(sorted, 33));
        p66ByBracket.put(e.getKey(), percentile(sorted, 66));
    }
    log.info("Size-bracketed percentiles: {}", p33ByBracket.keySet().stream()
        .map(k -> k + "[p33=" + String.format("%.1f", p33ByBracket.get(k))
                + ", p66=" + String.format("%.1f", p66ByBracket.get(k)) + "]")
        .collect(Collectors.joining(", ")));

    // Step 3 — assign marks using the bracket-specific thresholds
    for (poagingreport r : reports) {
        double pct = useUpdated
            ? (r.getUpdatedPercentAging() != null ? r.getUpdatedPercentAging() : 0.0)
            : (r.getPercentAging()        != null ? r.getPercentAging()        : 0.0);
        int count = useUpdated
            ? (r.getUpdatedCountPOOver180() != null ? r.getUpdatedCountPOOver180() : 0)
            : (r.getCountPOOver180()        != null ? r.getCountPOOver180()        : 0);

        String bracket = r.getSizeBracket() != null ? r.getSizeBracket() : "Medium";
        double bp33 = p33ByBracket.getOrDefault(bracket, 0.0);
        double bp66 = p66ByBracket.getOrDefault(bracket, 0.0);

        int mark = count == 0 ? 3
                 : pct <= bp33 ? 3
                 : pct <= bp66 ? 2
                 : 1;

        if (useUpdated) r.setUpdatedMarks(mark);
        else { r.setMarks(mark); r.setUpdatedMarks(mark); }
    }
    return reports;
    }

    private double percentile(List<Double> sorted, int pct) {
        if (sorted.isEmpty()) return 0;
        int n = sorted.size();
        double rank = (pct/100.0) * (n - 1);
        int lower = (int) Math.floor(rank);
        int upper = Math.min(lower + 1, n - 1);
        double frac = rank - lower;
        return sorted.get(lower) + frac * (sorted.get(upper) - sorted.get(lower));
    }

    // ══════════════════════════════════════════════════════════════
    //  Build Dashboard DTO
    //
    //  ✅ Percentile fix: only stations WITH PO>180 are included
    //  in percentile calc — matches Excel formula behaviour.
    //  Previously ALL stations (including 0%) were included,
    //  which dragged 33rd percentile down to 0.0%.
    // ══════════════════════════════════════════════════════════════
    private POAgingDashboardDTO buildDashboardDTO(List<poagingreport> reports) {
        POAgingDashboardDTO d = new POAgingDashboardDTO();

        d.setTotalPOOver180(reports.stream()
            .mapToInt(r -> r.getCountPOOver180() != null ? r.getCountPOOver180() : 0).sum());
        d.setUpdatedTotalPOOver180(reports.stream()
            .mapToInt(r -> r.getUpdatedCountPOOver180() != null ? r.getUpdatedCountPOOver180() : 0).sum());
        d.setTotalOutstandingValue(reports.stream()
            .mapToDouble(r -> r.getTotalOutstandingValue() != null ? r.getTotalOutstandingValue() : 0.0).sum());
        d.setUpdatedTotalOutstandingValue(reports.stream()
            .mapToDouble(r -> r.getUpdatedOutstandingValue() != null ? r.getUpdatedOutstandingValue() : 0.0).sum());
        d.setAveragePercentAging(reports.stream()
            .mapToDouble(r -> r.getPercentAging() != null ? r.getPercentAging() : 0.0)
            .average().orElse(0.0));
        d.setUpdatedAveragePercentAging(reports.stream()
            .mapToDouble(r -> r.getUpdatedPercentAging() != null ? r.getUpdatedPercentAging() : 0.0)
            .average().orElse(0.0));
        d.setHighAgingStations((int) reports.stream()
            .filter(r -> Integer.valueOf(1).equals(r.getUpdatedMarks())).count());
        d.setMediumAgingStations((int) reports.stream()
            .filter(r -> Integer.valueOf(2).equals(r.getUpdatedMarks())).count());
        d.setLowAgingStations((int) reports.stream()
            .filter(r -> Integer.valueOf(3).equals(r.getUpdatedMarks())).count());
        d.setTotalStations(reports.size());
        d.setTotalPOCleared(reports.stream()
            .mapToInt(r -> r.getFullyClearedCount() != null ? r.getFullyClearedCount() : 0).sum());
        d.setTotalPOPartiallyPaid(reports.stream()
            .mapToInt(r -> r.getPartiallyPaidCount() != null ? r.getPartiallyPaidCount() : 0).sum());
        d.setTotalClearedAmount(reports.stream()
            .mapToDouble(r -> r.getTotalClearedAmount() != null ? r.getTotalClearedAmount() : 0.0).sum());

        // ✅ FIX — percentile uses only stations WITH PO > 180
        // =PERCENTILE(IF(F2:F33>0, D2:D33), 0.33)
        List<Double> pctsForPercentile = reports.stream()
        .filter(r -> r.getTotalPOByStation() != null && r.getTotalPOByStation() > 0)
        .map(r -> r.getUpdatedPercentAging())
        .filter(Objects::nonNull)
        .sorted()
        .collect(Collectors.toList());

        double p33 = percentile(pctsForPercentile, 33);
        double p66 = percentile(pctsForPercentile, 66);
        d.setPercentile33(p33);
        d.setPercentile66(p66);

        log.info("Dashboard percentiles (updated, with-PO-only) — 33rd: {}%, 66th: {}%",
            String.format("%.2f", p33), String.format("%.2f", p66));

        d.setMarkDistribution(Map.of(
            "High Aging (1)",   d.getHighAgingStations(),
            "Medium Aging (2)", d.getMediumAgingStations(),
            "Low Aging (3)",    d.getLowAgingStations()
        ));
        d.setStationData(reports.stream()
            .map(this::toDTO)
            .sorted(Comparator.comparingInt(
                (POAgingReportDTO r) -> r.getUpdatedCountPOOver180() != null
                    ? r.getUpdatedCountPOOver180() : 0).reversed())
            .collect(Collectors.toList()));
        d.setSubzoneSummary(buildSubzoneSummary(reports));
        return d;
    }

    private List<SubzoneSummaryDTO> buildSubzoneSummary(List<poagingreport> reports) {
        return reports.stream()
                .collect(Collectors.groupingBy(
                    r -> r.getSubzone() != null ? r.getSubzone() : "Unknown"))
                .entrySet().stream().map(entry -> {
                    String subzone = entry.getKey();
                    List<poagingreport> sr = entry.getValue();
                    SubzoneSummaryDTO dto = new SubzoneSummaryDTO();
                    dto.setSubzone(subzone);
                    dto.setSubzoneLabel(SUBZONE_LABELS.getOrDefault(subzone, subzone));
                    dto.setTotalStations(sr.size());
                    dto.setTotalPOOver180(sr.stream()
                        .mapToInt(r -> r.getCountPOOver180() != null ? r.getCountPOOver180() : 0).sum());
                    dto.setUpdatedTotalPOOver180(sr.stream()
                        .mapToInt(r -> r.getUpdatedCountPOOver180() != null ? r.getUpdatedCountPOOver180() : 0).sum());
                    dto.setTotalOutstandingValue(sr.stream()
                        .mapToDouble(r -> r.getTotalOutstandingValue() != null ? r.getTotalOutstandingValue() : 0.0).sum());
                    dto.setUpdatedOutstandingValue(sr.stream()
                        .mapToDouble(r -> r.getUpdatedOutstandingValue() != null ? r.getUpdatedOutstandingValue() : 0.0).sum());
                    double avgMark = sr.stream()
                        .mapToInt(r -> r.getUpdatedMarks() != null ? r.getUpdatedMarks() : 3)
                        .average().orElse(3.0);
                    dto.setMarks(Math.max(1, Math.min(3, (int) Math.round(avgMark))));
                    dto.setHighAgingCount((int) sr.stream()
                        .filter(r -> Integer.valueOf(1).equals(r.getUpdatedMarks())).count());
                    dto.setMediumAgingCount((int) sr.stream()
                        .filter(r -> Integer.valueOf(2).equals(r.getUpdatedMarks())).count());
                    dto.setLowAgingCount((int) sr.stream()
                        .filter(r -> Integer.valueOf(3).equals(r.getUpdatedMarks())).count());
                    return dto;
                })
                .sorted(Comparator.comparingInt(dto -> {
                    int i = SUBZONE_ORDER.indexOf(dto.getSubzone());
                    return i == -1 ? Integer.MAX_VALUE : i;
                }))
                .collect(Collectors.toList());
    }

    public void updateCachedAnalysis(UUID uploadId, Map<String, Object> analysis) {
        cacheService.loadCacheByUploadId(uploadId).ifPresent(dto -> {
            dto.setAiAnalysis(analysis);
            fileUploadRepository.findById(uploadId).ifPresent(upload ->
                cacheService.saveCache(uploadId, upload, getCurrentUsername(), dto, true));
        });
    }

    private String buildRemarks(int total, int fullyCleared,
                                int partiallyPaid, double remaining) {
        if (fullyCleared == 0 && partiallyPaid == 0) return null;
        List<String> parts = new ArrayList<>();
        if (fullyCleared  > 0) parts.add(fullyCleared  + " PO" + (fullyCleared  > 1 ? "s" : "") + " fully cleared");
        if (partiallyPaid > 0) parts.add(partiallyPaid + " PO" + (partiallyPaid > 1 ? "s" : "") + " partially paid");
        if (remaining     > 0) parts.add(String.format("RM %.2f remaining", remaining));
        return String.join(" · ", parts);
    }

    private void saveRawPORows(List<poagingreport> reports, List<Map<String,String>> poOver180) {
        Map<String, poagingreport> reportMap = reports.stream()
                .collect(Collectors.toMap(
                    p -> Objects.requireNonNull(p).getBusArea(), r -> r, (a, b) -> a));
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
                    rd.put(headers.get(j), getCellValue(
                        row.getCell(j, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK)));
                rows.add(rd);
            }
        }
        log.info("Read {} data rows from Excel", rows.size());
        return rows;
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
                try {
                    double v = cell.getNumericCellValue();
                    yield v % 1 == 0 ? String.valueOf((long) v) : String.valueOf(v);
                } catch (Exception e) { yield cell.getStringCellValue().trim(); }
            }
            default -> "";
        };
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

        // NEW — size bracket + trend
        dto.setSizeBracket(r.getSizeBracket());
        dto.setPreviousPercentAging(r.getPreviousPercentAging());
        dto.setTrend(computeTrend(r.getPreviousPercentAging(), r.getUpdatedPercentAging()));

        return dto;
    }

    // NEW helper
    private String computeTrend(Double previous, Double current) {
        if (previous == null || current == null) return null;
        double delta = current - previous;
        if (Math.abs(delta) < 0.5) return "Flat";        // treat <0.5% change as noise
        return delta < 0 ? "Improving" : "Worsening";
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