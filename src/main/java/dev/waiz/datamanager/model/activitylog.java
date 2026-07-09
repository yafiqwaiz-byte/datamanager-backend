package dev.waiz.datamanager.model;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "activity_log")
public class activitylog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "activity_id")
    private UUID activityId;

    // e.g. "Letter", "Export", "Form", "Map"
    @Column(name = "type")
    private String type;

    // Human-readable description, e.g. "Field mapping review for Batch #14"
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    // e.g. "Letter Template", "Form Templates", "Export Data", "TNB Map"
    @Column(name = "module")
    private String module;

    // e.g. "pending", "active", "error", "reboot"
    @Column(name = "status")
    private String status;

    // Staff/user username who triggered the action, nullable for system events
    @Column(name = "performed_by")
    private String performedBy;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}