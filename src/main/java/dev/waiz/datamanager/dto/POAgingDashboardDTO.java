package dev.waiz.datamanager.dto;

import java.util.List;
import java.util.Map;

import lombok.Data;

@Data
public class POAgingDashboardDTO {
    // ── KPI Cards ──────────────────────────────────────────────────
    private Integer totalPOOver180;
    private Integer updatedTotalPOOver180;
    private Integer totalPOCleared;          // ✅ added

    private Double totalOutstandingValue;    // ✅ renamed from totalOutstandingRM
    private Double updatedTotalOutstandingValue; // ✅ renamed from updatedTotalOutstandingRM

    private Double averagePercentAging;
    private Double updatedAveragePercentAging;   // ✅ added

    private Integer totalStations;           // ✅ added
    private Integer highAgingStations;
    private Integer mediumAgingStations;
    private Integer lowAgingStations;

    // ── Percentile thresholds ──────────────────────────────────────
    private Double percentile33;
    private Double percentile66;

    // ── Chart data ─────────────────────────────────────────────────
    private List<POAgingReportDTO> stationData;
    private List<SubzoneSummaryDTO> subzoneSummary;
    private Map<String, Integer> markDistribution;
}
