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
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Data
@Entity
@Table(name = "form_answer")
public class formanswer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID answerId;

    @ManyToOne
    @JoinColumn(name = "submission_id")
    private formsubmission submission;

    @ManyToOne
    @JoinColumn(name = "field_id")
    private formfield field;

    @Column(columnDefinition = "TEXT")
    private String answerValue;

    private String position;

    public formanswer(formsubmission submission, formfield field, String answerValue, String position) {
        this.submission = submission;
        this.field = field;
        this.answerValue = answerValue;
        this.position = position;
    }

    public UUID getAnswerId() {
        return answerId;
    }

    public formsubmission getSubmission(){
        return submission;
    }

    public formfield getField(){
        return field;
    }

    public String getAnswerValue() {
        return answerValue;
    }

    public String getPosition(){
        return position;
    }

    public void setAnswerValue(String answerValue){
        this.answerValue = answerValue;
    }

    public void setPosition(String position){
        this.position = position;
    }

    public void setSubmission(formsubmission submission){
        this.submission = submission;
    }

    public void setField(formfield field){
        this.field = field;
    }

    public String toString() {
        return "formanswer{" +
                "answerId=" + answerId +
                ", submissionId=" + (submission != null ? submission.getSubmissionId() : null) +
                ", fieldId=" + (field != null ? field.getFieldId() : null) +
                ", answerValue='" + answerValue + '\'' +
                ", position='" + position + '\'' +
                '}';
    }

    
}
