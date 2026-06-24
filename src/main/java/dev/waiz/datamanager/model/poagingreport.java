package dev.waiz.datamanager.model;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "PO_aging_report")
public class poagingreport {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID reportId;

    @ManyToOne
    @JoinColumn(name = "upload_id")
    private fileupload upload;

    @OneToMany(mappedBy = "report",cascade = CascadeType.ALL,orphanRemoval = true)
    private List<poagingraw> rawRows;

    // ── Station identity ───────────────────────────────────────────
    @Column(name = "bus_area")
    private String busArea;          // e.g. "6231"

    @Column(name = "station_name")
    private String stationName;      // from BA_TO_STATION_MAP e.g. "TNB SEBERANG JAYA"

    @Column(name = "subzone")
    private String subzone;          // e.g. "P1"

    //Core metrics -Original Count(before clearing)

    @Column(name = "count_po_over_180")
    private Integer countPOOver180;

    @Column(name = "total_po_by_station")
    private Integer totalPOByStation;

    @Column(name = "total_outstanding_value")
    private Double totalOutstandingValue;

    @Column(name = "percent_aging")
    private Double percentAging;

    @Column(name = "marks")
    private Integer marks;

    // ── Updated Counts (after clearing) ───────────────────────────
    @Column(name = "updated_count_po_over_180")
    private Integer updatedCountPOOver180;

    @Column(name = "updated_outstanding_value")
    private Double updatedOutstandingValue;

    @Column(name = "updated_percent_aging")
    private Double updatedPercentAging;

    @Column(name = "updated_marks")
    private Integer updatedMarks;

    //- Partial payment tracking 

    @Column(name = "fully_cleared_count") // POs fully paid(value remaining = 0)
    @Builder.Default
    private Integer fullyClearedCount = 0;

    @Column(name = "partially_paid_count") // POs partially paid( value remaining >0)
    @Builder.Default
    private Integer partiallyPaidCount = 0;

    @Column(name = "total_cleared_amount") // total GR/SA value received
    @Builder.Default
    private Double totalClearedAmount = 0.0;

    @Column(name = "remarks",columnDefinition = "TEXT")
    private String remarks;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }





}
