package dev.waiz.datamanager.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import lombok.Data;

@Data
public class ProcessedRowDTO {

    private UUID rowId;
    private UUID  excelId;
    private Integer rowIndex;
    private String rowData;
    private String dataVersion;
    private OffsetDateTime createdAt;
}
