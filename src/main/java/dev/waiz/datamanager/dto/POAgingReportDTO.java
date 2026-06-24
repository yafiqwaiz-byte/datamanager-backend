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

    // Updated (after cleared PO upload)
    private Integer updatedCountPOOver180;
    private Double updatedOutstandingValue;
    private Double updatedPercentAging;
    private Integer updatedMarks;

    // Update Partial payment info
    private Integer fullyClearedCount; //POs fully paid
    private Integer partiallyPaidCount; // POs partially paid (value remaining > 0)
    private Double totalClearedAmount; // total GR/SA received so far 
    private String remarks; //human-readable summary
}
