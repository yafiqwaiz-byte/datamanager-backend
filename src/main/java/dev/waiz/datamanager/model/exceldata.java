package dev.waiz.datamanager.model;

import java.time.OffsetDateTime;

import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "upload_id")
    private fileupload upload;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "column_headers")
    private String columnHeaders;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "row_data")
    private String rowData;

    @Column(name = "row_count")
    private Integer rowCount;

    @Column(name = "processed_at")
    private OffsetDateTime processedAt;
}
