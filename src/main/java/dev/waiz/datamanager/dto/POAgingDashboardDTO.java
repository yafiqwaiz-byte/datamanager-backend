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
    private Integer updatedTotalPOOver180; //still outstanding after clearing         
    private Double totalOutstandingValue;    
    private Double updatedTotalOutstandingValue;  //remaining after clearing

    private Double averagePercentAging;
    private Double updatedAveragePercentAging;  

    // Station mark counts 
    private Integer totalStations;           
    private Integer highAgingStations; 
    private Integer mediumAgingStations;
    private Integer lowAgingStations;

    // Clearing summary 
    private Integer totalPOCleared; //fully cleared POs
    private Integer totalPOPartiallyPaid; //partially paid,still in aging
    private Double totalClearedAmount; // total GR/SA value received;

    // ── Percentile thresholds ──────────────────────────────────────
    private Double percentile33;
    private Double percentile66;

    // ── Chart data ─────────────────────────────────────────────────
    private List<POAgingReportDTO> stationData;
    private List<SubzoneSummaryDTO> subzoneSummary;
    private Map<String, Integer> markDistribution;
}
