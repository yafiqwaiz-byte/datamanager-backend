package dev.waiz.datamanager.service;

import dev.waiz.datamanager.dto.POAgingDashboardDTO;
import dev.waiz.datamanager.dto.POAgingReportDTO;
import dev.waiz.datamanager.dto.SubzoneSummaryDTO;
import dev.waiz.datamanager.model.fileupload;
import dev.waiz.datamanager.model.poagingraw;
import dev.waiz.datamanager.model.poagingreport;
import dev.waiz.datamanager.repository.FileUploadRepository;
import dev.waiz.datamanager.repository.POAgingRawRepository;
import dev.waiz.datamanager.repository.POAgingReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
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
    private final POAgingRawRepository rawRepository;
    private final FileUploadRepository fileUploadRepository;

    // ── BA Number → Subzone Mapping (from KML file) ───────────────
    private static final Map<String, String> BA_TO_SUBZONE_MAP = Map.ofEntries(
        // P1 - Pulau Pinang 1
        Map.entry("6231", "P1"),   // TNB SEBERANG JAYA
        Map.entry("6260", "P1"),   // TNB PULAU PINANG

        // P2 - Pulau Pinang 2
        Map.entry("6232", "P2"),   // TNB NIBONG TEBAL
        Map.entry("6230", "P2"),   // TNB BERTAM
        Map.entry("6261", "P2"),   // TNB BAYAN BARU

        // SGP/KLM - Sungai Petani / Kulim
        Map.entry("6290", "SGP/KLM"),  // TNB SUNGAI PETANI
        Map.entry("6294", "SGP/KLM"),  // TNB KULIM
        Map.entry("6292", "SGP/KLM"),  // TNB BALING
        Map.entry("6295", "SGP/KLM"),  // TNB BANDAR BAHARU
        Map.entry("6293", "SGP/KLM"),  // TNB SIK
        Map.entry("6244", "SGP/KLM"),  // TNB GUAR CHEMPEDAK

        // ALS/KAN - Alor Setar / Kangar
        Map.entry("6240", "ALS/KAN"),  // TNB ALOR SETAR
        Map.entry("6246", "ALS/KAN"),  // TNB PENDANG
        Map.entry("6245", "ALS/KAN"),  // TNB KUALA NERANG
        Map.entry("6243", "ALS/KAN"),  // TNB LANGKAWI
        Map.entry("6242", "ALS/KAN"),  // TNB JITRA
        Map.entry("6201", "ALS/KAN"),  // TNB KANGAR

        // A1 - Perak A1
        Map.entry("6210", "A1"),   // TNB IPOH
        Map.entry("6219", "A1"),   // TNB ULU KINTA
        Map.entry("6221", "A1"),   // TNB BATU GAJAH
        Map.entry("6211", "A1"),   // TNB KAMPAR
        Map.entry("6212", "A1"),   // TNB BIDOR
        Map.entry("6213", "A1"),   // TNB TANJONG MALIM

        // A2 - Perak A2
        Map.entry("6227", "A2"),   // TNB SRI MANJUNG
        Map.entry("6250", "A2"),   // TNB TELUK INTAN
        Map.entry("6218", "A2"),   // TNB SERI ISKANDAR
        Map.entry("6252", "A2"),   // TNB HUTAN MELINTANG

        // A3 - Perak A3
        Map.entry("6220", "A3"),   // TNB TAIPING
        Map.entry("6224", "A3"),   // TNB BAGAN SERAI
        Map.entry("6223", "A3"),   // TNB GERIK
        Map.entry("6222", "A3"),   // TNB KUALA KANGSAR
        Map.entry("6225", "A3")    // TNB SG. SIPUT
    );

    // ── BA Number → Station Name Mapping (from KML file) ─────────
    private static final Map<String, String> BA_TO_STATION_MAP = Map.ofEntries(
        Map.entry("6231", "TNB SEBERANG JAYA"),
        Map.entry("6260", "TNB PULAU PINANG"),
        Map.entry("6232", "TNB NIBONG TEBAL"),
        Map.entry("6230", "TNB BERTAM"),
        Map.entry("6261", "TNB BAYAN BARU"),
        Map.entry("6290", "TNB SUNGAI PETANI"),
        Map.entry("6294", "TNB KULIM"),
        Map.entry("6292", "TNB BALING"),
        Map.entry("6295", "TNB BANDAR BAHARU"),
        Map.entry("6293", "TNB SIK"),
        Map.entry("6244", "TNB GUAR CHEMPEDAK"),
        Map.entry("6240", "TNB ALOR SETAR"),
        Map.entry("6246", "TNB PENDANG"),
        Map.entry("6245", "TNB KUALA NERANG"),
        Map.entry("6243", "TNB LANGKAWI"),
        Map.entry("6242", "TNB JITRA"),
        Map.entry("6201", "TNB KANGAR"),
        Map.entry("6210", "TNB IPOH"),
        Map.entry("6219", "TNB ULU KINTA"),
        Map.entry("6221", "TNB BATU GAJAH"),
        Map.entry("6211", "TNB KAMPAR"),
        Map.entry("6212", "TNB BIDOR"),
        Map.entry("6213", "TNB TANJONG MALIM"),
        Map.entry("6227", "TNB SRI MANJUNG"),
        Map.entry("6250", "TNB TELUK INTAN"),
        Map.entry("6218", "TNB SERI ISKANDAR"),
        Map.entry("6252", "TNB HUTAN MELINTANG"),
        Map.entry("6220", "TNB TAIPING"),
        Map.entry("6224", "TNB BAGAN SERAI"),
        Map.entry("6223", "TNB GERIK"),
        Map.entry("6222", "TNB KUALA KANGSAR"),
        Map.entry("6225", "TNB SG. SIPUT")
    );

    // ── Subzone display labels ─────────────────────────────────────
    private static final Map<String, String> SUBZONE_LABELS = Map.of(
        "P1",      "Pulau Pinang 1",
        "P2",      "Pulau Pinang 2",
        "SGP/KLM", "Sungai Petani / Kulim",
        "ALS/KAN", "Alor Setar / Kangar",
        "A1",      "Perak A1",
        "A2",      "Perak A2",
        "A3",      "Perak A3"
    );

    // ── Subzone sort order ─────────────────────────────────────────
    private static final List<String> SUBZONE_ORDER =
        List.of("P1", "P2", "SGP/KLM", "ALS/KAN", "A1", "A2", "A3");

    // ══════════════════════════════════════════════════════════════
    //  STEP 1: Process Raw PO Data Upload
    // ══════════════════════════════════════════════════════════════
    public POAgingDashboardDTO processRawPOData(
            MultipartFile file, UUID uploadId) throws Exception {

        fileupload upload = fileUploadRepository.findById(uploadId)
                .orElseThrow(() -> new RuntimeException(
                    "Upload not found: " + uploadId));

        // ── Read Excel ─────────────────────────────────────────────
        List<Map<String, String>> allRows = readExcelFile(file);
        log.info("Total rows read: {}", allRows.size());

        if (allRows.isEmpty()) {
            throw new RuntimeException(
                "Excel file is empty or has no data rows.");
        }

        // ── Filter POs > 180 days ──────────────────────────────────
        List<Map<String, String>> poOver180 = allRows.stream()
                .filter(row -> {
                    try {
                        String days = row
                            .getOrDefault("No. of days Outstanding", "0")
                            .replace(",", "")
                            .trim();
                        return Double.parseDouble(days) > 180;
                    } catch (Exception e) {
                        return false;
                    }
                })
                .collect(Collectors.toList());

        log.info("POs over 180 days: {}", poOver180.size());

          Set<String> uniqueBA = poOver180.stream()
            .map(r -> r.getOrDefault("Bus.Area", "").trim())
            .filter(ba -> !ba.isEmpty())
            .collect(Collectors.toSet());

        log.info("Unique Bus.Area in POs > 180:{}",uniqueBA.size());
        log.info("Bus Area found:{}",uniqueBA);

        // ── Delete existing reports for this upload (re-upload case) ──
        List<poagingreport> existing =
            reportRepository.findByUpload_UploadId(uploadId);
        log.info("Existing reports to delete:{}",existing.size());

        if (!existing.isEmpty()) {
            existing.forEach(r ->{
                List<poagingraw> rawtoDelete = 
                rawRepository.findByReport_ReportId(r.getReportId());
                log.info("Deleting {} raw rows for reports:{}",
                rawtoDelete.size(),r.getReportId());
                rawRepository.deleteAll(rawtoDelete);
            });
            reportRepository.deleteAll(existing);
            log.info("Deleted {} existing reports for re-upload", existing.size());
        }

        // ── Group by Bus.Area (BA Number) ─────────────────────────
        Map<String, List<Map<String, String>>> byBusArea = poOver180.stream()
                .collect(Collectors.groupingBy(
                    row -> row.getOrDefault("Bus.Area", "Unknown")
                              .trim()));

        // ── Build report per station ───────────────────────────────
        List<poagingreport> reports = new ArrayList<>();

        for (Map.Entry<String, List<Map<String, String>>> entry
                : byBusArea.entrySet()) {

            String busArea       = entry.getKey();
            List<Map<String, String>> stationRows = entry.getValue();

            // Look up station name and subzone from KML mapping
            String stationName = BA_TO_STATION_MAP.getOrDefault(
                busArea,
                stationRows.get(0).getOrDefault("Company Name", "Unknown"));
            String subzone = BA_TO_SUBZONE_MAP.getOrDefault(
                busArea, "Unknown");
           
            // Count ALL POs for this station (not just > 180)
            int totalPO = (int) allRows.stream()
                    .filter(r -> busArea.equals(
                        r.getOrDefault("Bus.Area", "").trim()))
                    .count();

            // Sum of Outstanding PO value for POs > 180
            double totalOutstanding = stationRows.stream()
                    .mapToDouble(r -> parseDouble(
                        r.getOrDefault("Outstanding PO value", "0")))
                    .sum();

            // Percent aging
            double percentAging = totalPO > 0
                    ? (double) stationRows.size() / totalPO * 100
                    : 0;

            poagingreport report = poagingreport.builder()
                    .upload(upload)
                    .stationName(stationName)
                    .busArea(busArea)
                    .subzone(subzone)
                    .countPOOver180(stationRows.size())
                    .totalPOByStation(totalPO)
                    .totalOutstandingValue(totalOutstanding)
                    .percentAging(percentAging)
                    .updatedCountPOOver180(stationRows.size())
                    .updatedOutstandingValue(totalOutstanding)
                    .updatedPercentAging(percentAging)
                    .build();

            reports.add(report);
        }

        // ── Also add stations with 0 PO > 180 (mark = 3) ─────────
        Set<String> processedBAs = byBusArea.keySet();
        Set<String> allBAs = allRows.stream()
                .map(r -> r.getOrDefault("Bus.Area", "").trim())
                .filter(ba -> !ba.isEmpty())
                .collect(Collectors.toSet());

        for (String busArea : allBAs) {
            if (processedBAs.contains(busArea)) continue;

            String stationName = BA_TO_STATION_MAP.getOrDefault(
                busArea,
                allRows.stream()
                    .filter(r -> busArea.equals(
                        r.getOrDefault("Bus.Area", "").trim()))
                    .findFirst()
                    .map(r -> r.getOrDefault("Company Name", "Unknown"))
                    .orElse("Unknown"));
            String subzone = BA_TO_SUBZONE_MAP.getOrDefault(
                busArea, "Unknown");

            int totalPO = (int) allRows.stream()
                    .filter(r -> busArea.equals(
                        r.getOrDefault("Bus.Area", "").trim()))
                    .count();

            poagingreport zeroReport = poagingreport.builder()
                    .upload(upload)
                    .stationName(stationName)
                    .busArea(busArea)
                    .subzone(subzone)
                    .countPOOver180(0)
                    .totalPOByStation(totalPO)
                    .totalOutstandingValue(0.0)
                    .percentAging(0.0)
                    .updatedCountPOOver180(0)
                    .updatedOutstandingValue(0.0)
                    .updatedPercentAging(0.0)
                    .build();

            reports.add(zeroReport);
        }

        // ── Calculate Marks (33rd/66th percentile) ─────────────────
        reports = calculateMarks(reports, false);

        // ── Save all reports ───────────────────────────────────────
        List<poagingreport> saved = reportRepository.saveAll(reports);
        log.info("Saved {} station reports", saved.size());

        // ── Save raw PO rows ───────────────────────────────────────
        saveRawPORows(saved, poOver180);

        // ── Build and return dashboard DTO ─────────────────────────
        return buildDashboardDTO(saved);
    }

    // ══════════════════════════════════════════════════════════════
    //  STEP 2: Process Cleared PO File
    // ══════════════════════════════════════════════════════════════
    public POAgingDashboardDTO processClearedPOFile(
            MultipartFile file, UUID uploadId) throws Exception {

        // ── Read cleared PO file ───────────────────────────────────
        List<Map<String, String>> rows = readExcelFile(file);
        log.info("Cleared PO file rows: {}", rows.size());

        // ── Get PO numbers where CLEARED? = YES ───────────────────
        List<String> clearedPONumbers = rows.stream()
                .filter(row -> "YES".equalsIgnoreCase(
                    row.getOrDefault("CLEARED?", "NO").trim()))
                .map(row -> row.getOrDefault("PO No.", "").trim())
                .filter(po -> !po.isEmpty())
                .distinct()
                .collect(Collectors.toList());

        log.info("POs marked as CLEARED: {}", clearedPONumbers.size());

        if (clearedPONumbers.isEmpty()) {
            log.warn("No cleared POs found in file. " +
                     "Check if CLEARED? column has YES values.");
        }

        // ── Mark POs as cleared in DB ──────────────────────────────
        List<poagingraw> rawRows =
            rawRepository.findByPoNumberIn(clearedPONumbers);

        for (poagingraw raw : rawRows) {
            raw.setIsCleared(true);
            raw.setClearedAt(OffsetDateTime.now());
        }
        rawRepository.saveAll(rawRows);
        log.info("Marked {} raw PO rows as cleared", rawRows.size());

        // ── Recalculate counts per station ─────────────────────────
        List<poagingreport> reports =
            reportRepository.findByUpload_UploadId(uploadId);

        if (reports.isEmpty()) {
            throw new RuntimeException(
                "No reports found for upload: " + uploadId +
                ". Please upload raw PO data first.");
        }

        for (poagingreport report : reports) {
            List<poagingraw> reportRows =
                rawRepository.findByReport_ReportId(report.getReportId());

            // Count remaining uncleared POs
            long remaining = reportRows.stream()
                    .filter(r -> !r.getIsCleared())
                    .count();

            // Sum remaining outstanding value
            double remainingAmount = reportRows.stream()
                    .filter(r -> !r.getIsCleared())
                    .mapToDouble(r -> r.getOutstandingPOValue() != null
                        ? r.getOutstandingPOValue() : 0.0)
                    .sum();

            // Update counts
            report.setUpdatedCountPOOver180((int) remaining);
            report.setUpdatedOutstandingValue(remainingAmount);

            // Recalculate percent aging
            double updatedPct = report.getTotalPOByStation() > 0
                    ? (double) remaining /
                      report.getTotalPOByStation() * 100
                    : 0.0;
            report.setUpdatedPercentAging(updatedPct);
        }

        // ── Recalculate marks with updated percentages ─────────────
        reports = calculateMarks(reports, true);
        reportRepository.saveAll(reports);
        log.info("Updated {} reports after clearing", reports.size());

        return buildDashboardDTO(reports);
    }

    // ══════════════════════════════════════════════════════════════
    //  STEP 3: Get Dashboard by Upload ID
    // ══════════════════════════════════════════════════════════════
    public POAgingDashboardDTO getDashboardByUploadId(UUID uploadId) {
        List<poagingreport> reports =
            reportRepository.findByUpload_UploadId(uploadId);

        if (reports.isEmpty()) {
            throw new RuntimeException(
                "No reports found for upload: " + uploadId);
        }

        return buildDashboardDTO(reports);
    }

    // ══════════════════════════════════════════════════════════════
    //  STEP 4: Get Latest Dashboard
    // ══════════════════════════════════════════════════════════════
    public POAgingDashboardDTO getLatestDashboard() {
        List<poagingreport> reports = reportRepository.findLatestReports();

        if (reports.isEmpty()) {
            throw new RuntimeException(
                "No PO Aging reports found. Please upload data first.");
        }

        return buildDashboardDTO(reports);
    }

    // ══════════════════════════════════════════════════════════════
    //  Calculate Marks using 33rd/66th Percentile
    // ══════════════════════════════════════════════════════════════
    private List<poagingreport> calculateMarks(
            List<poagingreport> reports, boolean useUpdated) {

        // Get percent aging values (use updated or original)
        List<Double> percentages = reports.stream()
                .map(r -> useUpdated
                    ? r.getUpdatedPercentAging()
                    : r.getPercentAging())
                .filter(Objects::nonNull)
                .sorted()
                .collect(Collectors.toList());

        if (percentages.isEmpty()) return reports;

        // Calculate percentiles
        double p33 = percentile(percentages, 33);
        double p66 = percentile(percentages, 66);

        log.info("Percentile calc - 33rd: {}, 66th: {} (useUpdated: {})",
                 p33, p66, useUpdated);

        // Assign marks
        for (poagingreport report : reports) {
            double pct = useUpdated
                ? (report.getUpdatedPercentAging() != null
                    ? report.getUpdatedPercentAging() : 0.0)
                : (report.getPercentAging() != null
                    ? report.getPercentAging() : 0.0);

            int countOver180 = useUpdated
                ? (report.getUpdatedCountPOOver180() != null
                    ? report.getUpdatedCountPOOver180() : 0)
                : (report.getCountPOOver180() != null
                    ? report.getCountPOOver180() : 0);

            int mark;

            if (countOver180 == 0) {
                // No PO > 180 = Best performance = Mark 3
                mark = 3;
            } else if (pct <= p33) {
                // Low aging = Mark 3 (Green)
                mark = 3;
            } else if (pct <= p66) {
                // Medium aging = Mark 2 (Yellow)
                mark = 2;
            } else {
                // High aging = Mark 1 (Red)
                mark = 1;
            }

            if (useUpdated) {
                report.setUpdatedMarks(mark);
            } else {
                report.setMarks(mark);
                report.setUpdatedMarks(mark); // set both initially
            }
        }

        return reports;
    }

    private double percentile(List<Double> sortedValues, int percentile) {
        if (sortedValues.isEmpty()) return 0;
        int index = (int) Math.ceil(percentile / 100.0 * sortedValues.size()) - 1;
        index = Math.max(0, Math.min(index, sortedValues.size() - 1));
        return sortedValues.get(index);
    }

    // ══════════════════════════════════════════════════════════════
    //  Build Dashboard DTO
    // ══════════════════════════════════════════════════════════════
    private POAgingDashboardDTO buildDashboardDTO(
            List<poagingreport> reports) {

        POAgingDashboardDTO dashboard = new POAgingDashboardDTO();

        // ── KPI Cards ──────────────────────────────────────────────
        dashboard.setTotalPOOver180(
            reports.stream()
                   .mapToInt(r -> r.getCountPOOver180() != null
                       ? r.getCountPOOver180() : 0)
                   .sum());

        dashboard.setUpdatedTotalPOOver180(
            reports.stream()
                   .mapToInt(r -> r.getUpdatedCountPOOver180() != null
                       ? r.getUpdatedCountPOOver180() : 0)
                   .sum());

        dashboard.setTotalOutstandingValue(
            reports.stream()
                   .mapToDouble(r -> r.getTotalOutstandingValue() != null
                       ? r.getTotalOutstandingValue() : 0.0)
                   .sum());

        dashboard.setUpdatedTotalOutstandingValue(
            reports.stream()
                   .mapToDouble(r -> r.getUpdatedOutstandingValue() != null
                       ? r.getUpdatedOutstandingValue() : 0.0)
                   .sum());

        dashboard.setAveragePercentAging(
            reports.stream()
                   .mapToDouble(r -> r.getPercentAging() != null
                       ? r.getPercentAging() : 0.0)
                   .average()
                   .orElse(0.0));

        dashboard.setUpdatedAveragePercentAging(
            reports.stream()
                   .mapToDouble(r -> r.getUpdatedPercentAging() != null
                       ? r.getUpdatedPercentAging() : 0.0)
                   .average()
                   .orElse(0.0));

        // ── Mark counts ────────────────────────────────────────────
        dashboard.setHighAgingStations(
            (int) reports.stream()
                         .filter(r -> Integer.valueOf(1)
                             .equals(r.getUpdatedMarks()))
                         .count());

        dashboard.setMediumAgingStations(
            (int) reports.stream()
                         .filter(r -> Integer.valueOf(2)
                             .equals(r.getUpdatedMarks()))
                         .count());

        dashboard.setLowAgingStations(
            (int) reports.stream()
                         .filter(r -> Integer.valueOf(3)
                             .equals(r.getUpdatedMarks()))
                         .count());

        dashboard.setTotalStations(reports.size());

        // ── POs cleared (difference) ───────────────────────────────
        dashboard.setTotalPOCleared(
            dashboard.getTotalPOOver180() -
            dashboard.getUpdatedTotalPOOver180());

        // ── Percentile thresholds ──────────────────────────────────
        List<Double> percentages = reports.stream()
                .map(poagingreport::getPercentAging)
                .filter(Objects::nonNull)
                .sorted()
                .collect(Collectors.toList());

        dashboard.setPercentile33(percentile(percentages, 33));
        dashboard.setPercentile66(percentile(percentages, 66));

        // ── Mark distribution for pie chart ───────────────────────
        dashboard.setMarkDistribution(Map.of(
            "High Aging (1)",   dashboard.getHighAgingStations(),
            "Medium Aging (2)", dashboard.getMediumAgingStations(),
            "Low Aging (3)",    dashboard.getLowAgingStations()
        ));

        // ── Station data (sorted by updated count desc) ────────────
        List<POAgingReportDTO> stationData = reports.stream()
                .map(this::toDTO)
                .sorted(Comparator.comparingInt(
                    (POAgingReportDTO d) -> d.getUpdatedCountPOOver180() != null
                        ? d.getUpdatedCountPOOver180() : 0)
                    .reversed())
                .collect(Collectors.toList());

        dashboard.setStationData(stationData);

        // ── Subzone summary ────────────────────────────────────────
        dashboard.setSubzoneSummary(buildSubzoneSummary(reports));

        return dashboard;
    }

    // ══════════════════════════════════════════════════════════════
    //  Build Subzone Summary
    // ══════════════════════════════════════════════════════════════
    private List<SubzoneSummaryDTO> buildSubzoneSummary(List<poagingreport> reports) {

        Map<String, List<poagingreport>> bySubzone = reports.stream()
                .collect(Collectors.groupingBy(
                    r -> r.getSubzone() != null
                        ? r.getSubzone() : "Unknown"));

        return bySubzone.entrySet().stream().map(entry -> {
            String subzone = entry.getKey();
            List<poagingreport> subzoneReports = entry.getValue();

            SubzoneSummaryDTO dto = new SubzoneSummaryDTO();
            dto.setSubzone(subzone);
            dto.setSubzoneLabel(
                SUBZONE_LABELS.getOrDefault(subzone, subzone));

            dto.setTotalStations(subzoneReports.size());

            dto.setTotalPOOver180(
                subzoneReports.stream()
                    .mapToInt(r -> r.getCountPOOver180() != null
                        ? r.getCountPOOver180() : 0)
                    .sum());

            dto.setUpdatedTotalPOOver180(
                subzoneReports.stream()
                    .mapToInt(r -> r.getUpdatedCountPOOver180() != null
                        ? r.getUpdatedCountPOOver180() : 0)
                    .sum());

            dto.setTotalOutstandingValue(
                subzoneReports.stream()
                    .mapToDouble(r -> r.getTotalOutstandingValue() != null
                        ? r.getTotalOutstandingValue() : 0.0)
                    .sum());

            dto.setUpdatedOutstandingValue(
                subzoneReports.stream()
                    .mapToDouble(r -> r.getUpdatedOutstandingValue() != null
                        ? r.getUpdatedOutstandingValue() : 0.0)
                    .sum());

            // Subzone mark = worst mark among stations
            int worstMark = subzoneReports.stream()
                    .mapToInt(r -> r.getUpdatedMarks() != null
                        ? r.getUpdatedMarks() : 3)
                    .min()
                    .orElse(3);
            dto.setMarks(worstMark);

            // Count by mark within subzone
            dto.setHighAgingCount(
                (int) subzoneReports.stream()
                    .filter(r -> Integer.valueOf(1)
                        .equals(r.getUpdatedMarks()))
                    .count());
            dto.setMediumAgingCount(
                (int) subzoneReports.stream()
                    .filter(r -> Integer.valueOf(2)
                        .equals(r.getUpdatedMarks()))
                    .count());
            dto.setLowAgingCount(
                (int) subzoneReports.stream()
                    .filter(r -> Integer.valueOf(3)
                        .equals(r.getUpdatedMarks()))
                    .count());

            return dto;
        })
        // Sort by SUBZONE_ORDER
        .sorted(Comparator.comparing(dto ->{
            int idx = SUBZONE_ORDER.indexOf(dto.getSubzone());
            return idx == -1 ? Integer.MAX_VALUE :idx;
        }))
        .collect(Collectors.toList());
    }

    // ══════════════════════════════════════════════════════════════
    //  Save Raw PO Rows
    // ══════════════════════════════════════════════════════════════
    private void saveRawPORows(
            List<poagingreport> reports,
            List<Map<String, String>> poOver180) {

        // Map busArea → report for quick lookup
        Map<String, poagingreport> reportMap = reports.stream()
                .collect(Collectors.toMap(
                    poagingreport::getBusArea,
                    r -> r,
                    (a, b) -> a));  // keep first if duplicate

        List<poagingraw> rawRows = new ArrayList<>();

        for (Map<String, String> row : poOver180) {
            String busArea = row.getOrDefault("Bus.Area", "").trim();
            poagingreport report = reportMap.get(busArea);

            if (report == null) {
                log.warn("No report found for Bus.Area: {}", busArea);
                continue;
            }

            boolean isCleared = "YES".equalsIgnoreCase(
                row.getOrDefault("CLEARED?", "NO").trim());

            poagingraw raw = poagingraw.builder()
                    .report(report)
                    // ── Identifiers ────────────────────────────────
                    .poNumber(row.getOrDefault("PO No.", ""))
                    .poItem(row.getOrDefault("PO Item", ""))
                    .poDescription(row.getOrDefault("PO Description", ""))
                    // ── Business ───────────────────────────────────
                    .busArea(busArea)
                    .purchGroup(row.getOrDefault("Purch.Group", ""))
                    .companyCode(row.getOrDefault("Company Code", ""))
                    .companyName(row.getOrDefault("Company Name", ""))
                    // ── Vendor ─────────────────────────────────────
                    .vendorAccNo(row.getOrDefault("Vendor Acc.No.", ""))
                    .vendorName(row.getOrDefault("Name", ""))
                    // ── Dates ──────────────────────────────────────
                    .createdDate(row.getOrDefault("Created Date", ""))
                    .itemDeliveryDate(
                        row.getOrDefault("Item Delivery Date", ""))
                    .validityStart(row.getOrDefault("Validity Start", ""))
                    .validityEnd(row.getOrDefault("Validity End", ""))
                    // ── Requisitioner ──────────────────────────────
                    .requisitioner(row.getOrDefault("Requisitioner", ""))
                    .requisitionerName(
                        row.getOrDefault("Requisitioner Name", ""))
                    .requisitionerEmail(
                        row.getOrDefault("Requisitioner Email", ""))
                    .requisitionerDepartment(
                        row.getOrDefault("Requisitioner Department", ""))
                    .requisitionerDivision(
                        row.getOrDefault("Requisitioner Division", ""))
                    // ── PR Creator ─────────────────────────────────
                    .prCreator(row.getOrDefault("PR Creator", ""))
                    .prCreatorName(row.getOrDefault("PR Creator Name", ""))
                    .prCreatorEmail(
                        row.getOrDefault("PR Creator Email", ""))
                    .prCreatorDepartment(
                        row.getOrDefault("PR Creator Department", ""))
                    .prCreatorDivision(
                        row.getOrDefault("PR Creator Division", ""))
                    // ── Outline Agreement ──────────────────────────
                    .outlineAgreementNo(
                        row.getOrDefault("Outline Agreement No.", ""))
                    .outlineAgreementItem(
                        row.getOrDefault("Outline Agreement Item", ""))
                    .outlineAgreementDescription(
                        row.getOrDefault(
                            "Outline Agreement Description", ""))
                    // ── Financial ──────────────────────────────────
                    .netOrderValue(
                        parseDouble(
                            row.getOrDefault("Net Order Value", "0")))
                    .currency(row.getOrDefault("Curr", ""))
                    .exchangeRate(
                        parseDouble(
                            row.getOrDefault("Exchange rate", "0")))
                    .outstandingPOValue(
                        parseDouble(
                            row.getOrDefault("Outstanding PO value", "0")))
                    .trackingNo(row.getOrDefault("Tracking No.", ""))
                    .heldPO(row.getOrDefault("Held PO", ""))
                    // ── Key Aging ──────────────────────────────────
                    .noOfDaysOutstanding(
                        parseInt(
                            row.getOrDefault(
                                "No. of days Outstanding", "0")))
                    .isCleared(isCleared)
                    .clearedAt(isCleared ? OffsetDateTime.now() : null)
                    .build();

            rawRows.add(raw);
        }

        log.info("Total raw rows to save:{}",rawRows.size());
        log.info("POOver180 input size:{}",poOver180.size());
        log.info("Reports mapped:{}",reports.size());
        rawRepository.saveAll(rawRows);
        log.info("Saved {} raw PO rows", rawRows.size());
    }

    // ══════════════════════════════════════════════════════════════
    //  Read Excel File
    // ══════════════════════════════════════════════════════════════
    private List<Map<String, String>> readExcelFile(
            MultipartFile file) throws Exception {

        List<Map<String, String>> rows = new ArrayList<>();

        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(0);

            if (headerRow == null) {
                throw new RuntimeException(
                    "Excel file has no header row.");
            }

            // Read headers
            List<String> headers = new ArrayList<>();
            for (Cell cell : headerRow) {
                headers.add(cell.getStringCellValue().trim());
            }
            log.info("Headers found: {}", headers);

            // Read data rows
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                // Skip completely empty rows
                boolean isEmpty = true;
                for (int j = 0; j < headers.size(); j++) {
                    Cell cell = row.getCell(j,
                        Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                    if (cell != null &&
                        cell.getCellType() != CellType.BLANK) {
                        isEmpty = false;
                        break;
                    }
                }
                if (isEmpty) continue;

                Map<String, String> rowData = new LinkedHashMap<>();
                for (int j = 0; j < headers.size(); j++) {
                    Cell cell = row.getCell(j,
                        Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                    rowData.put(headers.get(j), getCellValue(cell));
                }
                rows.add(rowData);
            }
        }

        log.info("Read {} data rows from Excel", rows.size());
        return rows;
    }

    // ══════════════════════════════════════════════════════════════
    //  Convert to DTO
    // ══════════════════════════════════════════════════════════════
    private POAgingReportDTO toDTO(poagingreport report) {
        POAgingReportDTO dto = new POAgingReportDTO();
        dto.setReportId(report.getReportId());
        dto.setStationName(report.getStationName());
        dto.setBusArea(report.getBusArea());
        dto.setSubzone(report.getSubzone());
        dto.setSubzoneLabel(
            SUBZONE_LABELS.getOrDefault(
                report.getSubzone(), report.getSubzone()));
        dto.setCountPOOver180(report.getCountPOOver180());
        dto.setTotalPOByStation(report.getTotalPOByStation());
        dto.setTotalOutstandingValue(report.getTotalOutstandingValue());
        dto.setPercentAging(report.getPercentAging());
        dto.setMarks(report.getMarks());
        dto.setUpdatedCountPOOver180(report.getUpdatedCountPOOver180());
        dto.setUpdatedOutstandingValue(report.getUpdatedOutstandingValue());
        dto.setUpdatedPercentAging(report.getUpdatedPercentAging());
        dto.setUpdatedMarks(report.getUpdatedMarks());
        return dto;
    }

    // ══════════════════════════════════════════════════════════════
    //  Helper Methods
    // ══════════════════════════════════════════════════════════════
    private String getCellValue(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getLocalDateTimeCellValue()
                              .toLocalDate().toString();
                }
                double val = cell.getNumericCellValue();
                // Return as integer if whole number
                yield val % 1 == 0
                    ? String.valueOf((long) val)
                    : String.valueOf(val);
            }
            case STRING  -> cell.getStringCellValue().trim();
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try {
                    double val = cell.getNumericCellValue();
                    yield val % 1 == 0
                        ? String.valueOf((long) val)
                        : String.valueOf(val);
                } catch (Exception e) {
                    yield cell.getStringCellValue().trim();
                }
            }
            default -> "";
        };
    }

    private Double parseDouble(String value) {
        if (value == null || value.isBlank()) return 0.0;
        try {
            return Double.parseDouble(
                value.replace(",", "")
                     .replace("RM", "")
                     .trim());
        } catch (Exception e) {
            return 0.0;
        }
    }

    private Integer parseInt(String value) {
        if (value == null || value.isBlank()) return 0;
        try {
            return (int) Double.parseDouble(
                value.replace(",", "").trim());
        } catch (Exception e) {
            return 0;
        }
    }
}