package dev.waiz.datamanager.service;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
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

    private final String uploadDir= "uploads/form-files/";

    public UUID saveSubmission(
        UUID templateId,
        String inputMethod,
        Map<String, String> allParams,
        MultiValueMap<String, MultipartFile> allFiles
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
            allFiles.forEach((key, files) -> {
                if (key.startsWith("file_") && files != null && !files.isEmpty()) {
                    UUID fieldId = UUID.fromString(key.replace("file_", ""));
                    formfield field = formFieldRepository.findById(fieldId)
                        .orElseThrow(() -> new RuntimeException("Field not found: " + fieldId));

                    List<String> savedPaths = new java.util.ArrayList<>();    
                    for (MultipartFile file : files) {
                    if (file == null || file.isEmpty()) continue;
                    try {
                        String originalName = file.getOriginalFilename();
                        String extension = originalName != null && originalName.contains(".")
                            ? originalName.substring(originalName.lastIndexOf("."))
                            : "";
                        String filename = UUID.randomUUID() + extension;

                        Path savePath = Paths.get(uploadDir + filename);
                        Files.createDirectories(savePath.getParent());
                        Files.write(savePath, file.getBytes());

                        savedPaths.add(uploadDir + filename);
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to save file: " + e.getMessage());
                    }
                }
                // ✅ Store all paths as comma-separated in ONE answer row
                if (!savedPaths.isEmpty()) {
                    formanswer answer = new formanswer();
                    answer.setSubmission(submission);
                    answer.setField(field);
                    answer.setAnswerValue(String.join(",", savedPaths)); // e.g. "uploads/form-files/a.jpg,uploads/form-files/b.jpg"
                    formAnswerRepository.save(answer);
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
