package dev.waiz.datamanager.service;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
import jakarta.transaction.Transactional;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.Comparator;
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

    @Transactional
    public UUID saveSubmission(
        UUID templateId,
        String inputMethod,
        Map<String, String> allParams,
        MultiValueMap<String, MultipartFile> allFiles
    ) {
        // TEMP LOG — remove after debugging
    System.out.println("=== ALL PARAMS ===");
    allParams.forEach((k, v) -> System.out.println(k + " = " + v));
    System.out.println("==================");

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

            if (key.equals("templateId") || key.equals("inputMethod")) return;

            if (key.startsWith("answer_")) {
                UUID fieldId = UUID.fromString(key.replace("answer_", ""));
                formfield field = formFieldRepository.findById(fieldId)
                    .orElseThrow(() -> new RuntimeException("Field not found: " + fieldId));

                formanswer answer= new formanswer();
                answer.setSubmission(submission);
                answer.setField(field);
                answer.setAnswerValue(value);
                formAnswerRepository.save(answer);
            } else {

                String cleanKey = key
                                  .trim()
                                  .replaceAll("^['^`\"\\-_*#@!]+", "")
                                  .replaceAll("['^`\"\\-_*#@!]+$", "")
                                  .replaceAll("\\s+", " ")
                                  .trim();
                
                if(!cleanKey.isEmpty()){
                formFieldRepository.findByTemplate_TemplateIdAndFieldLabelIgnoreCase(templateId, cleanKey)
                .ifPresent(field -> {
                    formanswer answer = new formanswer();
                    answer.setSubmission(submission);
                    answer.setField(field);
                    answer.setAnswerValue(value);
                    formAnswerRepository.save(answer);
                });
            }
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


    @Transactional
    public Page<SubmissionResponseDTO> getSubmissionsByTemplate(UUID templateId,int page,int size){
        Pageable pageable = PageRequest.of(page, size,Sort.by("submittedAt").descending());

        Page<UUID> idPage = formSubmissionRepository.findIdsByTemplateId(templateId,pageable);

        List<formsubmission> submissions = idPage.getContent().isEmpty()
        ? List.of():formSubmissionRepository.findByIdsWithAnswers(idPage.getContent());

        List<UUID> orderedIDs = idPage.getContent();
        submissions.sort(Comparator.comparingInt(s -> orderedIDs.indexOf(s.getSubmissionId())));



       List<SubmissionResponseDTO> dtos = submissions.stream()
       .map(submit -> new SubmissionResponseDTO(
            submit.getSubmissionId(),
            submit.getTemplate().getTemplateName(),
            submit.getInputMethod(),
            submit.getSubmittedAt(),
            submit.getStatus(),
            submit.getAnswers().stream()
                .map(answer -> new AnswerResponseDTO(
                    answer.getAnswerId(),
                    answer.getField().getFieldLabel(),
                    answer.getAnswerValue()
                ))
                .collect(Collectors.toList())
        ))
        .collect(Collectors.toList());
        return new PageImpl<>(dtos,pageable,idPage.getTotalElements());
    }

   
    @Transactional
    public Page<SubmissionResponseDTO> getMySubmissions(int page,int size) {
    String username = SecurityContextHolder.getContext()
        .getAuthentication().getName();
    account account = accountRepository.findByUsername(username)
        .orElseThrow(() -> new RuntimeException("Account not found"));
    user user = userRepository.findByAccount_AccountId(account.getAccountId())
        .orElseThrow(() -> new RuntimeException("User not found"));

    Pageable pageable = PageRequest.of(page, size, Sort.by("submittedAt").descending());

    Page<UUID> idPage = formSubmissionRepository.findIdsByUser(user, pageable);

    List<formsubmission> submissions = idPage.getContent().isEmpty()
    ?List.of():formSubmissionRepository.findByIdsWithAnswers(idPage.getContent());

    List<UUID> orderedIds = idPage.getContent();
    submissions.sort(Comparator.comparingInt(s -> orderedIds.indexOf(s.getSubmissionId())));


    List<SubmissionResponseDTO> dtos = submissions.stream()
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
        return new PageImpl<>(dtos,pageable,idPage.getTotalElements());
}

    @Transactional
    public Page<SubmissionResponseDTO> getAllSubmissions(int page,int size){
        String username = SecurityContextHolder.getContext()
        .getAuthentication().getName();

        staff currentstaff = staffrepository.findByAccount_Username(username)
        .orElseThrow(() -> new RuntimeException("Staff not found"));

        Pageable pageable = PageRequest.of(page,size,Sort.by("submittedAt").descending());

        List<formtemplate> templates = formTemplateRepository.findByStaff(currentstaff);
        List<UUID> templateIds = templates.stream()
                                 .map(formtemplate::getTemplateId).collect(Collectors.toList());

        if ( (templateIds.isEmpty())) {
            return new PageImpl<>(List.of(), pageable, 0);
        }

        Page<UUID> idPage = formSubmissionRepository.findIdsByTemplateIds(templateIds,pageable);

        List<formsubmission> submissions = idPage.getContent().isEmpty() ? List.of():formSubmissionRepository.findByIdsWithAnswers(idPage.getContent());

        List<UUID> orderedIds = idPage.getContent();
        submissions.sort(Comparator.comparingInt(s -> orderedIds.indexOf(s.getSubmissionId())));
              

       List<SubmissionResponseDTO> dtos = submissions.stream()
                .map(sub -> new SubmissionResponseDTO(
                     sub.getSubmissionId(),
                     sub.getTemplate().getTemplateName(),
                     sub.getInputMethod(),
                     sub.getSubmittedAt(),
                     sub.getStatus(),
                     sub.getAnswers().stream()
                        .map(answer -> new AnswerResponseDTO(
                             answer.getAnswerId(),
                             answer.getField().getFieldLabel(),
                             answer.getAnswerValue()
                        ))
                        .collect(Collectors.toList())
                ))
                .collect(Collectors.toList()); 
    return new PageImpl<>(dtos,pageable,idPage.getTotalElements());
}

}
