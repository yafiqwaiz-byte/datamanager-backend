package dev.waiz.datamanager.model;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "po_aging_cache")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class poagingcache {


    @Id
    @GeneratedValue
    private UUID cacheId;

    @ManyToOne
    @JoinColumn(name = "upload_id", referencedColumnName = "upload_id")
    private fileupload upload;

    @ManyToOne
    @JoinColumn(name = "staff_id", referencedColumnName = "staff_id")
    private staff staff;

    @Column(name = "dashboard_json", columnDefinition = "TEXT")
    private String dashboardJson;

    private LocalDateTime cachedAt;

    private boolean hasCleared;

}
