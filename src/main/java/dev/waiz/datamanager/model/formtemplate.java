package dev.waiz.datamanager.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "form_template")
public class formtemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "template_id", updatable = false, nullable = false)
    private UUID templateId;

    @ManyToOne
    @JoinColumn(name="staff_id")
    private staff staff;

    private String templateName;
    private String description;
    private Boolean isActive;
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "template",cascade = CascadeType.ALL)
    private List<formfield> fields;

    public formtemplate(String templateName, String description, Boolean isActive) {
        this.templateName = templateName;
        this.description = description;
        this.isActive = isActive;
    }

    public UUID getTemplateId() {
        return templateId;
    }

    public String getTemplateName() {
        return templateName;
    }

    public String getDescription() {
        return description;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

        public List<formfield> getFields() {
            return fields;
        }

        public void setFields(List<formfield> fields) {
            this.fields = fields;
        }

        public void setTemplateId(UUID templateId) {
            this.templateId = templateId;
        }

        public void setTemplateName(String templateName) {
            this.templateName = templateName;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public void setIsActive(Boolean isActive) {
            this.isActive = isActive;
        }

        public void setCreatedAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
        }

        public String toString() {
            return "formtemplate{" +
                    "templateId=" + templateId +
                    ", templateName='" + templateName + '\'' +
                    ", description='" + description + '\'' +
                    ", isActive=" + isActive +
                    ", createdAt=" + createdAt +
                    '}';
        }
}
