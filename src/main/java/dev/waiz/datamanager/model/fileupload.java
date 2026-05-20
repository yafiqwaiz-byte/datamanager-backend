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
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "file_upload")
public class fileupload {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "upload_id")
    private UUID uploadId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id")
    @JsonIgnoreProperties({"account","hibernateLazyInitializer"})
    private user user;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "staff_id")
    @JsonIgnoreProperties({"account","hibernateLazyInitializer"})
    private staff staff;

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "file_type")
    private String fileType;

    @Column(name = "file_path")
    private String filePath;

    @Column(name = "uploaded_at")
    private OffsetDateTime uploadedAt;
}
