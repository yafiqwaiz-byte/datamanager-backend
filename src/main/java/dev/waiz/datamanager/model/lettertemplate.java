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
@Table(name = "letter_templates")
public class lettertemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID letterTemplateId;

    @ManyToOne
    @JoinColumn(name = "staff_id")
    private staff staffId;

    @Column(name = "template_name")
    private String templateName;

    @Column(name = "file_path")
    private String filePath;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "placeholder",columnDefinition = "JSONB")
    private String placeholderData; // JSON string to store placeholder keys and example values

    @Column(name = "created_at")
    private OffsetDateTime createdAt;
}
