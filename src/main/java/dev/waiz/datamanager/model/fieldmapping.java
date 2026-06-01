package dev.waiz.datamanager.model;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

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
@Table(name = "field_mapping")
public class fieldmapping {


    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "mapping_id")
    private UUID mappingId;

    @ManyToOne
    @JoinColumn(name = "ocr_id")
    @JsonIgnoreProperties({"upload","extractedText","errorLog"})
    private ocrresult ocr;

    @ManyToOne
    @JoinColumn(name = "letter_template_id")
    @JsonIgnoreProperties({"staffId","placeholderData","filePath"})
    private lettertemplate letterTemplate;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "mapped_fields")
    private String mappedFields;

    @Column(name = "status")
    private String status;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;
}
