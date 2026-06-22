package dev.waiz.datamanager.model;

import java.util.UUID;

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
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Builder
@Table(name="form_field")
public class formfield {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "field_id", updatable = false, nullable = false)
    private UUID fieldId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id")
    private formtemplate template;

    @Column(name = "field_label")
    private String fieldLabel;

    @Column(name = "field_type")
    private String fieldType; //text, number, date, dropdown, etc.

    @Column(name = "image_labels",columnDefinition = "TEXT")
    private String imageLabels;
    
    @Column(name = "is_required")
    private Boolean isRequired;

    @Column(name = "field_order")
    private Integer fieldOrder;

    private String placeholder;


}
