package dev.waiz.datamanager.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DuplicatePODTO {

    private String poNo;
    private Integer ocurrenceCount;
    private Double clearedAmount;
    private String subzone;
    private String busArea;
    private String stationName;

}
