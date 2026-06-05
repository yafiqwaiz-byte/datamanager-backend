package dev.waiz.datamanager.service;


import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.zwobble.mammoth.DocumentConverter;
import org.zwobble.mammoth.Result;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.waiz.datamanager.dto.LetterTemplateDTO;
import dev.waiz.datamanager.model.account;
import dev.waiz.datamanager.model.lettertemplate;
import dev.waiz.datamanager.model.staff;
import dev.waiz.datamanager.repository.LetterTemplateRepository;
import dev.waiz.datamanager.repository.accountrepository;
import dev.waiz.datamanager.repository.staffrepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class TemplateUploadService {

    private final LetterTemplateRepository letterTemplateRepository;
    private final staffrepository staffRepository;
    private final accountrepository accountRepository;
    private final ObjectMapper objectMapper;


    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\[([A-Z_]+)\\]");

    public lettertemplate uploadTemplate( String templateName, MultipartFile file) throws Exception {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        account acc = accountRepository.findByUsername(username)
                      .orElseThrow(() -> new RuntimeException("Account not found"));
        staff currenStaff = staffRepository.findByAccount_AccountId(acc.getAccountId()).orElseThrow(() -> new RuntimeException("Staff not found"));             
        // Create upload directory if it doesn't exist
        String uploadDir = System.getProperty("user.home") + "/datamanager/uploads/templatesletter/";
        Files.createDirectories(Paths.get(uploadDir));

        // Generate unique filename and save file
        String fileName = UUID.randomUUID() + "_" + file.getOriginalFilename();
        String filePath = uploadDir + fileName;
        
        // Save file to disk
       Files.copy(
            file.getInputStream(),
            Paths.get(filePath),
            StandardCopyOption.REPLACE_EXISTING
       );
       log.info("File saved to: {}",filePath);

        // Extract placeholders from the document
        List<String> placeholders = extractPlaceholders(filePath);
        log.info("Extracted {} placeholders from template: {}", placeholders.size(), placeholders);

        // Create and save template entity
        lettertemplate template = new lettertemplate();
        template.setStaffId(currenStaff);
        template.setTemplateName(templateName);
        template.setFilePath(filePath);
        template.setPlaceholderData(objectMapper.writeValueAsString(placeholders));
        template.setCreatedAt(OffsetDateTime.now());

        lettertemplate savedTemplate = letterTemplateRepository.save(template);
        log.info("Template saved with ID: {}", savedTemplate.getLetterTemplateId());

        return savedTemplate;
    }

    public String convertToHtml (String filePath) throws Exception {
        DocumentConverter converter = new DocumentConverter();
        Result<String> result = converter.convertToHtml(new File(filePath));

        log.info("Converted docx to HTML,warnings: {}",result.getWarnings());
        return result.getValue();
    }

    public lettertemplate savePlaceholders(UUID templateId,List<Map<String,String>> placeholderMappings) throws Exception{
        lettertemplate template = letterTemplateRepository.findById(templateId)
        .orElseThrow(()-> new RuntimeException("Template not found" + templateId));

        List<String> placeholderNames = placeholderMappings.stream()
                     .map(m -> m.get("placeholder"))
                     .collect(java.util.stream.Collectors.toList());

        updateDocxWithPlaceholders(template.getFilePath(),placeholderMappings);
        log.info("Updatede .docx with {} placeholders",placeholderNames.size());  
        
        template.setPlaceholderData(
            objectMapper.writeValueAsString(placeholderNames));
        lettertemplate saved = letterTemplateRepository.save(template);
        log.info("Placeholders saved to Database: {}",placeholderNames);
        
        return saved;
    }

    public List<lettertemplate> getTemplatesByStaff(UUID staffId) {
        return letterTemplateRepository.findByStaffId_StaffId(staffId);
    }

    public List<LetterTemplateDTO> getAllTemplates() {
        return letterTemplateRepository.findAll()
        .stream()
        .map(t -> new LetterTemplateDTO(
            t.getLetterTemplateId(),
            t.getTemplateName()
        ))
        .toList();
    }

    public lettertemplate getTemplateById(UUID templateId) {
    return letterTemplateRepository.findById(templateId)
        .orElseThrow(() -> new RuntimeException("Template not found"));
}

    private void updateDocxWithPlaceholders(String filePath,
                                            List<Map<String,String>> placeholderMappings) throws Exception{
            try (FileInputStream fis = new FileInputStream(filePath);
            XWPFDocument doc = new XWPFDocument(fis)) {

                for (XWPFParagraph para:doc.getParagraphs()){
                    replaceInParagraph(para,placeholderMappings);
                }

                for(XWPFTable table : doc.getTables()){
                    for(XWPFTableRow row : table.getRows()){
                        for(XWPFTableCell cell : row.getTableCells()){
                            for(XWPFParagraph para : cell.getParagraphs()){
                                replaceInParagraph(para, placeholderMappings);
                            }
                        }
                    }
                }

                try (FileOutputStream fos = new FileOutputStream(filePath)){
                    doc.write(fos);
                }
            }
    }

    private void replaceInParagraph(XWPFParagraph para,
        List<Map<String,String>> placeholderMappings) {
            for (XWPFRun run : para.getRuns()){
                String text = run.getText(0);
                if (text == null) continue;
                
                for (Map<String,String> mapping : placeholderMappings){
                    String ori = mapping.get("original");
                    String placeholder = mapping.get("placeholder");

                    if (text.contains(ori)){
                        text= text.replace(ori, placeholder);
                        log.info("Replace '{}' with '{}'", ori,placeholder);
                    }
                }
                run.setText(text,0);
            }
    }

    private List<String> extractPlaceholders(String filePath) throws Exception {
        Set<String> placeholders = new LinkedHashSet<>();

        try (FileInputStream fis = new FileInputStream(filePath);
            XWPFDocument doc = new XWPFDocument(fis)) {
            // Extract from paragraphs
            for (XWPFParagraph para : doc.getParagraphs()) {
               String fullText = para.getText();
               findPlaceholders(fullText, placeholders);

               StringBuilder runText = new StringBuilder();
               para.getRuns().forEach(run -> {
                if (run.getText(0)!=null){
                    runText.append(run.getText(0));
                }
               });
               findPlaceholders(runText.toString(), placeholders);
            }

            // Extract from tables
            for (XWPFTable table : doc.getTables()) {
                for (XWPFTableRow row : table.getRows()) {
                    for (XWPFTableCell cell : row.getTableCells()) {
                        for(XWPFParagraph para : cell.getParagraphs()){
                            findPlaceholders(para.getText(), placeholders);

                        StringBuilder runText = new StringBuilder();
                        para.getRuns().forEach(run -> {
                            if(run.getText(0)!= null){
                                runText.append(run.getText(0));
                            }
                        });
                        findPlaceholders(runText.toString(),placeholders);
                    }
                }
            }
        }
    }
    return new ArrayList<>(placeholders);
    }

    private void findPlaceholders(String text, Set<String> placeholders) {
        if (text == null || text.isEmpty()) {
            return;
        }

        Matcher matcher = PLACEHOLDER_PATTERN.matcher(text);
        while (matcher.find()) {
            placeholders.add("[" + matcher.group(1).trim() + "]");
        }
    }
}