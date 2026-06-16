package dev.waiz.datamanager.dto;

import java.util.UUID;

import lombok.Data;

@Data
public class POAgingReportDTO {

    private UUID reportId;
    private String stationName;
    private String busArea;
    private String subzone;
    private String subzoneLabel;

    // Original
    private Integer countPOOver180;
    private Integer totalPOByStation;
    private Double totalOutstandingValue;
    private Double percentAging;
    private Integer marks;

    // Updated after clearing
    private Integer updatedCountPOOver180;
    private Double updatedOutstandingValue;
    private Double updatedPercentAging;
    private Integer updatedMarks;
}
