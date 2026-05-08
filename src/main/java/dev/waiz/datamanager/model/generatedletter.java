package dev.waiz.datamanager.model;

import java.time.OffsetDateTime;
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
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "generated_letters")
public class generatedletter {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "letter_id")
    private UUID letterId;

    @ManyToOne
    @JoinColumn(name = "mapping_id")
    private fieldmapping mapping;

    @Column(name = "docx_path")
    private String docxPath;

    @Column(name = "pdf_path")
    private String pdfPath;

    @Column(name = "generated_at")
    private OffsetDateTime generatedAt;

}
