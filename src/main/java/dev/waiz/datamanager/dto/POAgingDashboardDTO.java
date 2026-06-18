package dev.waiz.datamanager.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import lombok.Data;

@Data
public class POAgingDashboardDTO {

    private UUID uploadId;
    // ── KPI Cards ──────────────────────────────────────────────────
    private Integer totalPOOver180;
    private Integer updatedTotalPOOver180;
    private Integer totalPOCleared;         

    private Double totalOutstandingValue;    
    private Double updatedTotalOutstandingValue; 

    private Double averagePercentAging;
    private Double updatedAveragePercentAging;  
    private Integer totalStations;           
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
