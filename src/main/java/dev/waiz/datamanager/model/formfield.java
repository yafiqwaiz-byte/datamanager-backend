package dev.waiz.datamanager.model;

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

    @ManyToOne
    @JoinColumn(name = "template_id")
    private formtemplate template;

    private String fieldLabel;
    private String fieldType; //text, number, date, dropdown, etc.
    private Boolean isRequired;
    private Integer fieldOrder;
    private String placeholder;

    public formfield(formtemplate template, String fieldLabel, String fieldType, Boolean isRequired, Integer fieldOrder, String placeholder) {
        this.template = template;
        this.fieldLabel = fieldLabel;
        this.fieldType = fieldType;
        this.isRequired = isRequired;
        this.fieldOrder = fieldOrder;
        this.placeholder = placeholder;
    }

}
