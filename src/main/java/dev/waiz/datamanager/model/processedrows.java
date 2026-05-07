package dev.waiz.datamanager.model;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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
@Table(name = "processed_rows")
public class processedrows {
 
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "row_id")
    private UUID rowId;

    @ManyToOne
    @JoinColumn(name = "excel_id")
    private exceldata excel;

    @Column(name = "data_version")
    private String dataVersion; // "raw" or "cleaned"

    @Column(name = "row_index")
    private Integer rowIndex;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "row_data",columnDefinition = "jsonb")
    private String rowData;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;
}
