package dev.waiz.datamanager.service;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import dev.waiz.datamanager.dto.AnswerResponseDTO;
import dev.waiz.datamanager.dto.SubmissionResponseDTO;
import dev.waiz.datamanager.model.account;
import dev.waiz.datamanager.model.formanswer;
import dev.waiz.datamanager.model.formfield;
import dev.waiz.datamanager.model.formsubmission;
import dev.waiz.datamanager.model.formtemplate;
import dev.waiz.datamanager.model.staff;
import dev.waiz.datamanager.model.user;
import dev.waiz.datamanager.repository.FormAnswerRepository;
import dev.waiz.datamanager.repository.FormFieldRepository;
import dev.waiz.datamanager.repository.FormSubmissionRepository;
import dev.waiz.datamanager.repository.accountrepository;
import dev.waiz.datamanager.repository.formtemplaterepository;
import dev.waiz.datamanager.repository.staffrepository;
import dev.waiz.datamanager.repository.userrepository;


import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class FormSubmissionService {

    @Autowired
    private FormSubmissionRepository formSubmissionRepository;
    
    @Autowired
    private formtemplaterepository formTemplateRepository;

    @Autowired
    private FormFieldRepository formFieldRepository;

    @Autowired
    private FormAnswerRepository formAnswerRepository;

    @Autowired
    private userrepository userRepository;

    @Autowired
    private staffrepository staffrepository;

    @Autowired
    private accountrepository accountRepository;

    private final String uploadDir= "uploads/form-files";

    public UUID saveSubmission(
        UUID templateId,
        String inputMethod,
        Map<String, String> allParams,
        Map<String, MultipartFile> allFiles
    ) {
        // 1. Get logged in user
        String username = SecurityContextHolder.getContext()
            .getAuthentication().getName();
        account account = accountRepository.findByUsername(username)
            .orElseThrow(() -> new RuntimeException("Account not found"));
        user user = userRepository.findByAccount_AccountId(account.getAccountId())
            .orElseThrow(() -> new RuntimeException("User not found"));

        // 2. Get template
        formtemplate template = formTemplateRepository.findById(templateId)
            .orElseThrow(() -> new RuntimeException("Template not found"));

        // 3. Create and save submission
        formsubmission submission = new formsubmission();
        submission.setUser(user);
        submission.setTemplate(template);
        submission.setInputMethod(inputMethod);
        submission.setSubmittedAt(LocalDateTime.now());
        submission.setStatus("submitted");
        formSubmissionRepository.save(submission);

        // 4. Save text/other answers
        allParams.forEach((key, value) -> {
            if (key.startsWith("answer_")) {
                UUID fieldId = UUID.fromString(key.replace("answer_", ""));
                formfield field = formFieldRepository.findById(fieldId)
                    .orElseThrow(() -> new RuntimeException("Field not found: " + fieldId));

                formanswer answer= new formanswer();
                answer.setSubmission(submission);
                answer.setField(field);
                answer.setAnswerValue(value);
                formAnswerRepository.save(answer);
            }
        });

        // 5. Save image/file uploads
        if (allFiles != null) {
            allFiles.forEach((key, file) -> {
                if (key.startsWith("file_") && file != null && !file.isEmpty()) {
                    UUID fieldId = UUID.fromString(key.replace("file_", ""));
                    formfield field = formFieldRepository.findById(fieldId)
                        .orElseThrow(() -> new RuntimeException("Field not found: " + fieldId));

                    try {
                        // Generate unique filename to avoid conflicts
                        String originalName = file.getOriginalFilename();
                        String extension = originalName != null && originalName.contains(".")
                            ? originalName.substring(originalName.lastIndexOf("."))
                            : "";
                        String filename = UUID.randomUUID() + extension;

                        // Save file to disk
                        Path savePath = Paths.get(uploadDir + filename);
                        Files.createDirectories(savePath.getParent());
                        Files.write(savePath, file.getBytes());

                        // Save file path as answer value
                        formanswer answer = new formanswer();
                        answer.setSubmission(submission);
                        answer.setField(field);
                        answer.setAnswerValue(uploadDir + filename);  // e.g. uploads/form-files/uuid.jpg
                        formAnswerRepository.save(answer);

                    } catch (IOException e) {
                        throw new RuntimeException("Failed to save file: " + e.getMessage());
                    }
                }
            });
        }
        return submission.getSubmissionId();
    }

    public List<SubmissionResponseDTO> getSubmissionsByTemplate(UUID templateId){
        return formSubmissionRepository.findByTemplate_TemplateId(templateId).stream()
        .map(submission -> new SubmissionResponseDTO(
            submission.getSubmissionId(),
            submission.getTemplate().getTemplateName(),
            submission.getInputMethod(),
            submission.getSubmittedAt(),
            submission.getStatus(),
            submission.getAnswers().stream()
                .map(answer -> new AnswerResponseDTO(
                    answer.getAnswerId(),
                    answer.getField().getFieldLabel(),
                    answer.getAnswerValue()
                ))
                .collect(Collectors.toList())
        ))
        .collect(Collectors.toList());
    }

   

    public List<SubmissionResponseDTO> getMySubmissions() {
    String username = SecurityContextHolder.getContext()
        .getAuthentication().getName();
    account account = accountRepository.findByUsername(username)
        .orElseThrow(() -> new RuntimeException("Account not found"));
    user user = userRepository.findByAccount_AccountId(account.getAccountId())
        .orElseThrow(() -> new RuntimeException("User not found"));

    return formSubmissionRepository.findByUser(user).stream()
        .map(submission -> new SubmissionResponseDTO(
            submission.getSubmissionId(),
            submission.getTemplate().getTemplateName(),
            submission.getInputMethod(),
            submission.getSubmittedAt(),
            submission.getStatus(),
            submission.getAnswers().stream()
                .map(answer -> new AnswerResponseDTO(
                    answer.getAnswerId(),
                    answer.getField().getFieldLabel(),
                    answer.getAnswerValue()
                ))
                .collect(Collectors.toList())
        ))
        .collect(Collectors.toList());
}

    public List<SubmissionResponseDTO> getAllSubmissions(){
        String username = SecurityContextHolder.getContext()
        .getAuthentication().getName();

        staff currentstaff = staffrepository.findByAccount_Username(username)
        .orElseThrow(() -> new RuntimeException("Staff not found"));

        return formTemplateRepository.findByStaff(currentstaff).stream()
            .flatMap(template ->
                formSubmissionRepository.findByTemplate_TemplateId(template.getTemplateId())
                .stream()
                .map(submission -> new SubmissionResponseDTO(
                     submission.getSubmissionId(),
                     submission.getTemplate().getTemplateName(),
                     submission.getInputMethod(),
                     submission.getSubmittedAt(),
                     submission.getStatus(),
                     submission.getAnswers().stream()
                        .map(answer -> new AnswerResponseDTO(
                             answer.getAnswerId(),
                             answer.getField().getFieldLabel(),
                             answer.getAnswerValue()
                        ))
                        .collect(Collectors.toList())
                ))
            )
            .collect(Collectors.toList());
}

}
