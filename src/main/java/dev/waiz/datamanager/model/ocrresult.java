package dev.waiz.datamanager.model;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

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
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "ocr_result")
public class ocrresult {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "ocr_id")
    private UUID ocrId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "upload_id")
    @JsonIgnoreProperties({"user", "staff"})
    private fileupload upload;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "selected_template_id")
    @JsonIgnoreProperties({"staffId", "placeholderData", "filePath", "createdAt"})
    private lettertemplate selectedTemplate;

    @Column(name = "extracted_text",columnDefinition = "TEXT")
    private String extractedText;

    @Column(name = "status")
    private String status;

    @Column(name = "error_log",columnDefinition = "TEXT")
    private String errorLog;

    

    @Column(name = "processed_at")
    private OffsetDateTime processedAt;
}
