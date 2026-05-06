package dev.waiz.datamanager.model;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "excel_data")
public class exceldata {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "excel_id")
    private UUID excelId;

    @ManyToOne
    @JoinColumn(name = "upload_id")
    private fileupload upload;

    @Column(name = "column_headers",columnDefinition = "jsonb")
    private String columnHeader;

    @Column(name = "row_data",columnDefinition = "jsonb")
    private String rowData;

    @Column(name = "row_count")
    private Integer rowCount;

    @Column(name = "processed_at")
    private OffsetDateTime processedAt;
}
