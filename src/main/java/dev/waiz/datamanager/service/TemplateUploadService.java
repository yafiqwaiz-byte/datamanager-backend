package dev.waiz.datamanager.service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
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
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.ObjectMapper;


import dev.waiz.datamanager.model.lettertemplate;
import dev.waiz.datamanager.model.staff;
import dev.waiz.datamanager.repository.LetterTemplateRepository;
import dev.waiz.datamanager.repository.staffrepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class TemplateUploadService {

    private final LetterTemplateRepository letterTemplateRepository;

    private final staffrepository staffRepository;

    private final ObjectMapper objectMapper;

    private static final String UPLOAD_DIR = "uploads/templatesletter/";

    private static final Pattern PlACEHOLDER_PATTERN = Pattern.compile("\\{\\{([^}]+)\\}\\}");

    public lettertemplate uploadTemplate(UUID staffId,String templateName,MultipartFile file) throws Exception{
       
        staff s = staffRepository.findById(staffId)
        .orElseThrow(() -> new RuntimeException("Staff not found:" + staffId));

        Files.createDirectories(Paths.get(UPLOAD_DIR));
        String fileName = UUID.randomUUID() + "_" + file.getOriginalFilename();
        String filePath = UPLOAD_DIR + fileName;
        file.transferTo(new File(filePath));

        List<String> placeholders = extractPlaceholders(file);
        log.info("Extracted {} placeholders from tempalte", placeholders.size());

        lettertemplate template = new lettertemplate();
        template.setStaffId(s);
        template.setTemplateName(templateName);
        template.setFilePath(filePath);
        template.setPlaceholderData(objectMapper.writeValueAsString(placeholders));
        template.setCreatedAt(OffsetDateTime.now());
        return letterTemplateRepository.save(template);

}

    public List<lettertemplate> getTemplatesByStaff(UUID staffId){
        return letterTemplateRepository.findByStaffId_StaffId(staffId);
    }

    public List<lettertemplate> getAllTemplates(){
        return letterTemplateRepository.findAll();
    }

    private List<String> extractPlaceholders(MultipartFile file) throws Exception{

        Set<String> placeholders = new LinkedHashSet<>();

        try (XWPFDocument doc = new XWPFDocument(file.getInputStream())) {

            for(XWPFParagraph para : doc.getParagraphs()){
                findPlaceholders(para.getText(),placeholders);
            }

            for (XWPFTable table : doc.getTables()) {
                for(XWPFTableRow row : table.getRows()){
                    for(XWPFTableCell cell : row.getTableCells()){
                        findPlaceholders(cell.getText(),placeholders);
                    }
                }
            }
        }
        return new ArrayList<>(placeholders);   
    }

    private void findPlaceholders(String text,Set<String> placeholders){
       Matcher matcher = PlACEHOLDER_PATTERN.matcher(text);
       while (matcher.find()) {
        placeholders.add("{{" + matcher.group(1).trim() + "}}");
       }
    }

}