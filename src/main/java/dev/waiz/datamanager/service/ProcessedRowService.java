package dev.waiz.datamanager.service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.waiz.datamanager.dto.ProcessedRowDTO;
import dev.waiz.datamanager.model.exceldata;
import dev.waiz.datamanager.model.processedrows;
import dev.waiz.datamanager.repository.ExcelDataRepository;
import dev.waiz.datamanager.repository.ProcessedRowsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessedRowService {

    private final ProcessedRowsRepository processedRowsRepository;
    private final ExcelDataRepository excelDataRepository;
    private final ObjectMapper objectMapper;

    

    public List<ProcessedRowDTO> saveProcessedRows(UUID excelId) throws Exception {

        exceldata excel = excelDataRepository.findById(excelId)
                .orElseThrow(() -> new RuntimeException("Excel data not found: " + excelId));

        List<Map<String, String>> rows = objectMapper.readValue(
                excel.getRowData(), new TypeReference<List<Map<String, String>>>() {});

        // ─── Detect column types ───────────────────────────────────
        Set<String> dateColumns = detectDateColumns(rows);
        Set<String> numericColumns = detectNumericColumns(rows, dateColumns);
                

        // ─── Compute stats ─────────────────────────────────────────
        Map<String, double[]> stats = computeStats(rows, numericColumns);
        Map<String, double[]> minMax = computeMinMax(rows, numericColumns);
        Map<String, double[]> zStats = computeZScoreStats(rows, numericColumns);
        Map<String, String> meanValues = computeMean(rows, numericColumns);
        Map<String, String> modeValues = computeMode(rows, numericColumns);
        Map<String, String> medianDates = computeMedianDate(rows, dateColumns);

        List<processedrows> savedRows = new ArrayList<>();

        for (int i = 0; i < rows.size(); i++) {
            Map<String, String> raw = rows.get(i);

            // ─── Save RAW version (for box plot) ──────────────────
            processedrows rawRow = new processedrows();
            rawRow.setExcel(excel);
            rawRow.setRowIndex(i + 1);
            rawRow.setRowData(objectMapper.writeValueAsString(raw));
            rawRow.setDataVersion("raw");
            rawRow.setCreatedAt(OffsetDateTime.now());
            savedRows.add(rawRow);                               // ← fixed

            // ─── Save CLEANED version ──────────────────────────────
            Map<String, String> cleaned = handleMissingValues(
                    raw, numericColumns, meanValues, modeValues);
            cleaned = cleanDateColumns(cleaned, dateColumns, medianDates);
            cleaned = handleOutliers(cleaned, numericColumns, stats);
            cleaned = handleExtremeValues(cleaned, numericColumns, zStats);
            cleaned = normalize(cleaned, numericColumns, minMax);
            cleaned = encodeCategorical(cleaned);
            cleaned = reduceDimensions(cleaned);

            processedrows cleanedRow = new processedrows();
            cleanedRow.setExcel(excel);
            cleanedRow.setRowIndex(i + 1);
            cleanedRow.setRowData(objectMapper.writeValueAsString(cleaned));
            cleanedRow.setDataVersion("cleaned");
            cleanedRow.setCreatedAt(OffsetDateTime.now());
            savedRows.add(cleanedRow);
        }

        List<processedrows> saved = processedRowsRepository.saveAll(savedRows);
        return saved.stream().map(this::toDTO).collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    public List<Map<String,Object>> getSampleRows(UUID excelId,int limit){
        Page<ProcessedRowDTO> page = getRowsByExcelId(excelId, "cleaned", 0, limit);

        List<Map<String,Object>> result = new ArrayList<>();
        for (ProcessedRowDTO row : page.getContent()){
            try {
                Map<String,Object> data = objectMapper.readValue(row.getRowData(),Map.class);
                result.add(data);
            } catch (Exception e){
                log.error("Failed to parse row data:{}",e.getMessage());
            }
        }
        return result;
    }

    public Page<ProcessedRowDTO> getRowsByExcelId(UUID excelId,String dataVersion, int page,int size) {
        Pageable pageable = PageRequest.of(page, size,Sort.by("rowIndex").ascending());

        Page<processedrows> rows = processedRowsRepository.findByExcel_ExcelIdAndDataVersion(excelId,dataVersion,pageable);
        return rows.map(this::toDTO);
    }


    private ProcessedRowDTO toDTO(processedrows row){
        ProcessedRowDTO dto = new ProcessedRowDTO();
        dto.setRowId(row.getRowId());
        dto.setExcelId(row.getExcel().getExcelId());
        dto.setRowIndex(row.getRowIndex());
        dto.setRowData(row.getRowData());
        dto.setDataVersion(row.getDataVersion());
        dto.setCreatedAt(row.getCreatedAt());
        return dto;
    }


    // ─── Missing Values ────────────────────────────────────────────
    private Map<String, String> handleMissingValues(
            Map<String, String> row,
            Set<String> numericColumns,
            Map<String, String> meanValues,
            Map<String, String> modeValues) {

        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : row.entrySet()) {
            String col = entry.getKey();
            String value = entry.getValue();

            if (value == null || value.trim().isEmpty()) {
                if (numericColumns.contains(col)) {
                    String mean = meanValues.getOrDefault(col, "0");
                    result.put(col, mean);
                    log.warn("Missing numeric filled with mean: col={} mean={}", col, mean);
                } else {
                    String mode = modeValues.getOrDefault(col, "N/A");
                    result.put(col, mode);
                    log.warn("Missing nominal filled with mode: col={} mode={}", col, mode);
                }
            } else {
                result.put(col, value.trim());
            }
        }
        return result;
    }

    // ─── Compute Mean ──────────────────────────────────────────────
    private Map<String, String> computeMean(
            List<Map<String, String>> rows,
            Set<String> numericColumns) {

        Map<String, String> means = new HashMap<>();
        for (String col : numericColumns) {
            OptionalDouble avg = rows.stream()
                    .map(r -> r.getOrDefault(col, ""))
                    .filter(v -> !v.isEmpty())
                    .mapToDouble(v -> {
                        try { return Double.parseDouble(v); }
                        catch (Exception e) { return Double.NaN; }
                    })
                    .filter(v -> !Double.isNaN(v))
                    .average();
            means.put(col, avg.isPresent()
                    ? String.format("%.4f", avg.getAsDouble()) : "0");
        }
        return means;
    }

    // ─── Compute Mode ──────────────────────────────────────────────
    private Map<String, String> computeMode(
            List<Map<String, String>> rows,
            Set<String> numericColumns) {

        Map<String, String> modes = new HashMap<>();
        if (rows.isEmpty()) return modes;

        for (String col : rows.get(0).keySet()) {
            if (numericColumns.contains(col)) continue;

            Map<String, Long> freq = rows.stream()
                    .map(r -> r.getOrDefault(col, ""))
                    .filter(v -> !v.isEmpty())
                    .collect(Collectors.groupingBy(v -> v, Collectors.counting()));

            freq.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .ifPresent(e -> modes.put(col, e.getKey()));
        }
        return modes;
    }

    // ─── Outlier Handling (IQR) ────────────────────────────────────
    private Map<String, String> handleOutliers(
            Map<String, String> row,
            Set<String> numericColumns,
            Map<String, double[]> stats) {

        Map<String, String> result = new LinkedHashMap<>(row);
        for (String col : numericColumns) {
            if (!row.containsKey(col) || row.get(col).equals("N/A")) continue;
            try {
                double value = Double.parseDouble(row.get(col));
                double[] iqr = stats.get(col);
                if (iqr == null) continue;

                if (value < iqr[2]) {
                    log.warn("Outlier capped: col={} value={} -> {}", col, value, iqr[2]);
                    result.put(col, String.valueOf(iqr[2]));
                } else if (value > iqr[3]) {
                    log.warn("Outlier capped: col={} value={} -> {}", col, value, iqr[3]);
                    result.put(col, String.valueOf(iqr[3]));
                }
            } catch (NumberFormatException ignored) {}
        }
        return result;
    }

    // ─── Extreme Value Handling (Z-Score) ─────────────────────────
    private Map<String, double[]> computeZScoreStats(
            List<Map<String, String>> rows,
            Set<String> numericColumns) {

        Map<String, double[]> zStats = new HashMap<>();
        for (String col : numericColumns) {
            List<Double> values = rows.stream()
                    .map(r -> r.getOrDefault(col, ""))
                    .filter(v -> !v.isEmpty())
                    .map(v -> { try { return Double.parseDouble(v); }
                                catch (Exception e) { return null; } })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            if (values.size() < 2) continue;

            double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            double stdDev = Math.sqrt(values.stream()
                    .mapToDouble(v -> Math.pow(v - mean, 2))
                    .average().orElse(0));

            zStats.put(col, new double[]{mean, stdDev});
        }
        return zStats;
    }

    private Map<String, String> handleExtremeValues(
            Map<String, String> row,
            Set<String> numericColumns,
            Map<String, double[]> zStats) {

        Map<String, String> result = new LinkedHashMap<>(row);
        for (String col : numericColumns) {
            if (!row.containsKey(col) || row.get(col).equals("N/A")) continue;
            try {
                double value = Double.parseDouble(row.get(col));
                double[] stat = zStats.get(col);
                if (stat == null || stat[1] == 0) continue;

                double zScore = Math.abs((value - stat[0]) / stat[1]);
                if (zScore > 3) {
                    double capped = value > stat[0]
                            ? stat[0] + (3 * stat[1])
                            : stat[0] - (3 * stat[1]);
                    log.warn("Extreme value capped: col={} value={} z={} capped={}",
                            col, value, String.format("%.2f", zScore), String.format("%.2f", capped));
                    result.put(col, String.format("%.4f", capped));
                    result.put(col + "_extreme_flag", "true");
                }
            } catch (NumberFormatException ignored) {}
        }
        return result;
    }

    // ─── Normalize (Min-Max) ───────────────────────────────────────
    private Map<String, String> normalize(
            Map<String, String> row,
            Set<String> numericColumns,
            Map<String, double[]> minMax) {

        Map<String, String> result = new LinkedHashMap<>(row);
        for (String col : numericColumns) {
            if (!row.containsKey(col) || row.get(col).equals("N/A")) continue;
            try {
                double value = Double.parseDouble(row.get(col));
                double[] mm = minMax.get(col);
                if (mm == null) continue;

                double normalized = (mm[1] - mm[0] == 0) ? 0
                        : (value - mm[0]) / (mm[1] - mm[0]);
                result.put(col + "_normalized", String.format("%.4f", normalized)); // ← fixed
            } catch (NumberFormatException ignored) {}
        }
        return result;
    }

    // ─── Encode Categorical ────────────────────────────────────────
    private Map<String, String> encodeCategorical(Map<String, String> row) {
        Map<String, String> result = new LinkedHashMap<>(row);
        for (Map.Entry<String, String> entry : row.entrySet()) {
            String val = entry.getValue().toLowerCase().trim();
            switch (val) {
                case "paid"     -> result.put(entry.getKey() + "_encoded", "1");
                case "unpaid"   -> result.put(entry.getKey() + "_encoded", "0");
                case "yes"      -> result.put(entry.getKey() + "_encoded", "1");
                case "no"       -> result.put(entry.getKey() + "_encoded", "0");
                case "true"     -> result.put(entry.getKey() + "_encoded", "1");
                case "false"    -> result.put(entry.getKey() + "_encoded", "0");
                case "active"   -> result.put(entry.getKey() + "_encoded", "1");
                case "inactive" -> result.put(entry.getKey() + "_encoded", "0");
                default -> {}
            }
        }
        return result;
    }

    // ─── Reduce Dimensions ─────────────────────────────────────────
    private Map<String, String> reduceDimensions(Map<String, String> row) {
        return row.entrySet().stream()
                .filter(e -> e.getValue() != null && !e.getValue().isEmpty())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (a, b) -> a,
                        LinkedHashMap::new));
    }

    // ─── Detect Numeric Columns ────────────────────────────────────
    private Set<String> detectNumericColumns(List<Map<String, String>> rows,Set<String> dateColumns) {
        Set<String> numeric = new HashSet<>();
        if (rows.isEmpty()) return numeric;

        for (String col : rows.get(0).keySet()) {
            if (dateColumns.contains(col)) continue;
            long numericCount = rows.stream()
                    .map(r -> r.getOrDefault(col, ""))
                    .filter(v -> {
                        try { Double.parseDouble(v); return true; }
                        catch (NumberFormatException e) { return false; }
                    }).count();

            if (numericCount > rows.size() * 0.8) numeric.add(col);
        }
        return numeric;
    }

    // ─── Compute IQR Stats ─────────────────────────────────────────
    private Map<String, double[]> computeStats(
            List<Map<String, String>> rows,
            Set<String> numericColumns) {

        Map<String, double[]> stats = new HashMap<>();
        for (String col : numericColumns) {
            List<Double> values = rows.stream()
                    .map(r -> r.getOrDefault(col, ""))
                    .filter(v -> !v.isEmpty())
                    .map(v -> { try { return Double.parseDouble(v); }
                                catch (Exception e) { return null; } })
                    .filter(Objects::nonNull)
                    .sorted()
                    .collect(Collectors.toList());

            if (values.size() < 4) continue;

            double q1 = values.get(values.size() / 4);
            double q3 = values.get(values.size() * 3 / 4);
            double iqr = q3 - q1;
            stats.put(col, new double[]{q1, q3, q1 - 1.5 * iqr, q3 + 1.5 * iqr});
        }
        return stats;
    }

    // ─── Compute Min-Max ───────────────────────────────────────────
    private Map<String, double[]> computeMinMax(
            List<Map<String, String>> rows,
            Set<String> numericColumns) {

        Map<String, double[]> minMax = new HashMap<>();
        for (String col : numericColumns) {
            DoubleSummaryStatistics s = rows.stream()
                    .map(r -> r.getOrDefault(col, ""))
                    .filter(v -> !v.isEmpty())
                    .mapToDouble(v -> { try { return Double.parseDouble(v); }
                                       catch (Exception e) { return Double.NaN; } })
                    .filter(v -> !Double.isNaN(v))
                    .summaryStatistics();
            minMax.put(col, new double[]{s.getMin(), s.getMax()});
        }
        return minMax;
    }

    // ─── Detect Date Columns ───────────────────────────────────────
    private Set<String> detectDateColumns(List<Map<String, String>> rows) {
        Set<String> dateColumns = new HashSet<>();
        if (rows.isEmpty()) return dateColumns;

        List<DateTimeFormatter> formatters = List.of(
                DateTimeFormatter.ofPattern("dd/MM/yyyy"),
                DateTimeFormatter.ofPattern("d/M/yyyy"),
                DateTimeFormatter.ofPattern("dd-MM-yyyy"),
                DateTimeFormatter.ofPattern("d-M-yyyy"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd"),
                DateTimeFormatter.ofPattern("yyyy/MM/dd")
        );

        for (String col : rows.get(0).keySet()) {
            long dateCount = rows.stream()
                    .map(r -> r.getOrDefault(col, ""))
                    .filter(v -> !v.isEmpty())
                    .filter(v -> isDate(v, formatters))
                    .count();

            if (dateCount > rows.size() * 0.8) dateColumns.add(col);
        }
        return dateColumns;
    }

    private boolean isDate(String value, List<DateTimeFormatter> formatters) {
        for (DateTimeFormatter fmt : formatters) {
            try { LocalDate.parse(value.trim(), fmt); return true; }
            catch (Exception ignored) {}
        }
        return false;
    }

    // ─── Clean Date Columns ────────────────────────────────────────
    private Map<String, String> cleanDateColumns(
            Map<String, String> row,
            Set<String> dateColumns,
            Map<String, String> medianDates) {

        Map<String, String> result = new LinkedHashMap<>(row);

        List<DateTimeFormatter> formatters = List.of(
                DateTimeFormatter.ofPattern("dd/MM/yyyy"),
                DateTimeFormatter.ofPattern("d/M/yyyy"),
                DateTimeFormatter.ofPattern("dd-MM-yyyy"),
                DateTimeFormatter.ofPattern("d-M-yyyy"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd"),
                DateTimeFormatter.ofPattern("yyyy/MM/dd")
        );

        DateTimeFormatter standard = DateTimeFormatter.ofPattern("dd/MM/yyyy");

        for (String col : dateColumns) {
            String value = row.getOrDefault(col, "").trim();

            if (value.isEmpty() || value.equals("N/A")) {
                String median = medianDates.getOrDefault(col, "");
                result.put(col, median);
                log.warn("Missing date filled with median: col={} median={}", col, median);
                continue;
            }

            LocalDate parsed = null;
            for (DateTimeFormatter fmt : formatters) {
                try { parsed = LocalDate.parse(value, fmt); break; }
                catch (Exception ignored) {}
            }

            if (parsed == null) {
                String median = medianDates.getOrDefault(col, "");
                result.put(col, median);
                log.warn("Invalid date replaced with median: col={} value={}", col, value);
            } else {
                result.put(col, parsed.format(standard));
                if (parsed.isAfter(LocalDate.now())) {
                    result.put(col + "_flag", "future_date");
                    log.warn("Future date flagged: col={} value={}", col, value);
                }
            }
        }
        return result;
    }

    // ─── Compute Median Date ───────────────────────────────────────
    private Map<String, String> computeMedianDate(
            List<Map<String, String>> rows,
            Set<String> dateColumns) {

        Map<String, String> medians = new HashMap<>();
        DateTimeFormatter standard = DateTimeFormatter.ofPattern("dd/MM/yyyy"); // ← fixed

        List<DateTimeFormatter> formatters = List.of(
                DateTimeFormatter.ofPattern("dd/MM/yyyy"),
                DateTimeFormatter.ofPattern("d/M/yyyy"),
                DateTimeFormatter.ofPattern("dd-MM-yyyy"),
                DateTimeFormatter.ofPattern("d-M-yyyy"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd"),
                DateTimeFormatter.ofPattern("yyyy/MM/dd")
        );

        for (String col : dateColumns) {
            List<LocalDate> dates = rows.stream()
                    .map(r -> r.getOrDefault(col, ""))
                    .filter(v -> !v.isEmpty())
                    .map(v -> {
                        for (DateTimeFormatter fmt : formatters) {
                            try { return LocalDate.parse(v.trim(), fmt); }
                            catch (Exception ignored) {}
                        }
                        return null;
                    })
                    .filter(Objects::nonNull)
                    .sorted()
                    .collect(Collectors.toList());

            if (!dates.isEmpty()) {
                medians.put(col, dates.get(dates.size() / 2).format(standard));
            }
        }
        return medians;
    }

    
}