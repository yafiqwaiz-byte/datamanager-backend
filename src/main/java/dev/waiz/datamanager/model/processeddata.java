package dev.waiz.datamanager.model;

import java.time.LocalDateTime;
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
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "processed_data")
public class processeddata {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID processedId;

    @ManyToOne
    @JoinColumn(name = "upload_id")
    private fileupload upload;

    @Column(name = "cleaned_data",columnDefinition = "TEXT")
    private String cleanedData;

    @Column(name = "validation_status")
    private String validationStatus;

    @Column(name = "error_log",columnDefinition = "TEXT")
    private String errorLog;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;
}
