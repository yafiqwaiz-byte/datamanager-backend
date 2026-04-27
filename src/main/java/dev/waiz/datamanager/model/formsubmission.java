package dev.waiz.datamanager.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;


import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "form_submission")
public class formsubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID submissionId;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private user user;

    @ManyToOne
    @JoinColumn(name = "template_id")
    private formtemplate template;

    private String inputMethod;
    private LocalDateTime submittedAt;
    private String status;

    @OneToMany(mappedBy = "submission",cascade = CascadeType.ALL)
    private List<formanswer> answers;

    public formsubmission(user user, formtemplate template, String inputMethod, LocalDateTime submittedAt, String status) {
        this.user = user;
        this.template = template;
        this.inputMethod = inputMethod;
        this.submittedAt = submittedAt;
        this.status = status;
    }

    public UUID getSubmissionId() {
        return submissionId;
    }

    public user getUser() {
        return user;
    }

    public formtemplate getTemplate() {
        return template;
    }

    public String getInputMethod() {
        return inputMethod;
    }

    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }

    public String getStatus() {
        return status;
    }

    public List<formanswer> getAnswers() {
        return answers;
    }

    public void setAnswers(List<formanswer> answers) {
        this.answers = answers;
    }

    public void setUser(user user) {
        this.user = user;
    }

    public void setTemplate(formtemplate template) {
        this.template = template;
    }

    public void setInputMethod(String inputMethod) {
        this.inputMethod = inputMethod;
    }

    public void setSubmittedAt(LocalDateTime submittedAt) {
        this.submittedAt = submittedAt;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setSubmissionId(UUID submissionId) {
        this.submissionId = submissionId;
    }

    public String toString() {
        return "formsubmission{" +
                "submissionId=" + submissionId +
                ", user=" + user +
                ", template=" + template +
                ", inputMethod='" + inputMethod + '\'' +
                ", submittedAt=" + submittedAt +
                ", status='" + status + '\'' +
                '}';
    }

    
}
