package dev.waiz.datamanager.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;


import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private user user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id")
    private formtemplate template;

    @Column(name = "input_method")
    private String inputMethod;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "status")
    private String status;

    @OneToMany(mappedBy = "submission",cascade = CascadeType.ALL,fetch = FetchType.LAZY)
    private List<formanswer> answers;

    public formsubmission(user user, formtemplate template, String inputMethod, LocalDateTime submittedAt, String status) {
        this.user = user;
        this.template = template;
        this.inputMethod = inputMethod;
        this.submittedAt = submittedAt;
        this.status = status;
    }

    
}
