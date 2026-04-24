package dev.waiz.datamanager.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubmissionResponseDTO {

    private UUID submissionId;
    private String templateName;
    private String inputMethod;
    private LocalDateTime submittedAt;
    private String status;
    private List<AnswerResponseDTO> answers;
}
