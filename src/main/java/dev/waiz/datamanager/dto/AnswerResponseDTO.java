package dev.waiz.datamanager.dto;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor

public class AnswerResponseDTO {
    private UUID answerId;
    private String fieldLabel;
    private String answerValue;
    private Integer fieldOrder;

}
