package dev.waiz.datamanager.dto;

import lombok.Data;

@Data
public class SubzoneSummaryDTO {
    private String subzone;
    private String subzoneLabel;        // ← ADD full name
    private Integer totalStations;
    private Integer totalPOOver180;
    private Integer updatedTotalPOOver180;  // ← ADD updated
    private Double totalOutstandingValue;
    private Double updatedOutstandingValue; // ← ADD updated
    private Integer marks;
    private Integer highAgingCount;
    private Integer mediumAgingCount;
    private Integer lowAgingCount;
}
