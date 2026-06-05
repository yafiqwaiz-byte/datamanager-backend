package dev.waiz.datamanager.service;

import java.util.*;
import java.io.*;
import java.nio.file.*;
import java.time.OffsetDateTime;

import org.apache.poi.xwpf.usermodel.*;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.waiz.datamanager.model.fieldmapping;
import dev.waiz.datamanager.model.generatedletter;
import dev.waiz.datamanager.repository.FieldMappingRepository;
import dev.waiz.datamanager.repository.GeneratedLetterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class LetterGeneratorService {

    private final FieldMappingRepository fieldMappingRepository;
    private final GeneratedLetterRepository generatedLetterRepository;
    private final ObjectMapper objectMapper;

    private static final String GENERATED_DIR = System.getProperty("user.home") 
    + "/datamanager/uploads/generated/";

    public generatedletter generateLetter(UUID mappingId) throws Exception {
        
        fieldmapping mapping = fieldMappingRepository.findById(mappingId)
        .orElseThrow(() -> new RuntimeException("Field mapping not found:" + mappingId));

        if (!mapping.getStatus().equals("confirmed")) {
            throw new RuntimeException("Mapping not confirmed yet by Staff");
        }

        Map<String,String> mappedFields = objectMapper.readValue(
            mapping.getMappedFields(),
            new TypeReference<Map<String,String>>() {});
        
            String templatePath = mapping.getLetterTemplate().getFilePath();
            Files.createDirectories(Paths.get(GENERATED_DIR));
            String outputFileName = UUID.randomUUID().toString();

            String docxpath = GENERATED_DIR + outputFileName + ".docx";
            generateDocx(templatePath, mappedFields, docxpath);


            String pdfpath = GENERATED_DIR + outputFileName + ".pdf";
            generatePdf(docxpath, pdfpath);

            generatedletter letter = new generatedletter();
            letter.setMapping(mapping);
            letter.setDocxPath(docxpath);
            letter.setPdfPath(pdfpath);
            letter.setGeneratedAt(OffsetDateTime.now());
            return generatedLetterRepository.save(letter);
    
    }

    public List<generatedletter> getLettersByMapping(UUID mappingId){
        return generatedLetterRepository.findByMapping_MappingId(mappingId);
    }

    private void generateDocx(String templatePath,
                                Map<String,String> mappedFields,
                                String outputPath) throws Exception {try (FileInputStream fis = new FileInputStream(templatePath);
         XWPFDocument doc = new XWPFDocument(fis)) {

        // ✅ Replace in normal paragraphs
        for (XWPFParagraph para : doc.getParagraphs()) {
            String text = para.getRuns().stream()
                .map(r -> r.getText(0) != null ? r.getText(0) : "")
                .collect(java.util.stream.Collectors.joining());

            // ✅ Handle [BODY_TEXT] placeholder directly
            if (text.contains("[BODY_TEXT]") && mappedFields.containsKey("[BODY_TEXT]")) {
                List<XWPFRun> runs = para.getRuns();
                if (!runs.isEmpty()) {
                    runs.get(0).setText(mappedFields.get("[BODY_TEXT]"), 0);
                    for (int i = 1; i < runs.size(); i++) {
                        runs.get(i).setText("", 0);
                    }
                }
            } else {
                replaceParagraph(para, mappedFields);
            }
        }

        // ✅ Replace in table cells
        for (XWPFTable table : doc.getTables()) {
            for (XWPFTableRow row : table.getRows()) {
                for (XWPFTableCell cell : row.getTableCells()) {
                    for (XWPFParagraph para : cell.getParagraphs()) {
                        String text = para.getRuns().stream()
                            .map(r -> r.getText(0) != null ? r.getText(0) : "")
                            .collect(java.util.stream.Collectors.joining());

                        if (text.contains("[BODY_TEXT]") && mappedFields.containsKey("[BODY_TEXT]")) {
                            List<XWPFRun> runs = para.getRuns();
                            if (!runs.isEmpty()) {
                                runs.get(0).setText(mappedFields.get("[BODY_TEXT]"), 0);
                                for (int i = 1; i < runs.size(); i++) {
                                    runs.get(i).setText("", 0);
                                }
                            }
                        } else {
                            replaceParagraph(para, mappedFields);
                        }
                    }
                }
            }
        }

        try (FileOutputStream fos = new FileOutputStream(outputPath)) {
            doc.write(fos);
        }
        log.info("Generated DOCX saved at {}", outputPath);
    }}

    private void replaceParagraph(XWPFParagraph para,
                                    Map<String,String> mappedFields){
                                        // Get full paragraph text
    String fullText = para.getRuns().stream()
        .map(run -> run.getText(0) != null ? run.getText(0) : "")
        .collect(java.util.stream.Collectors.joining());

    if (fullText.trim().isEmpty()) return;

    String updatedText = fullText;

    // ✅ Also handle direct placeholder replacement e.g. [TARIKH]
    for (Map.Entry<String, String> entry : mappedFields.entrySet()) {
        if (updatedText.contains(entry.getKey())) {
            String value = entry.getValue() != null ? entry.getValue() : "";
            updatedText = updatedText.replace(entry.getKey(),value)
            .replaceAll("\\s*:\\s*$", ""); // Remove trailing colon if value is empty
            log.info("Direct replaced '{}' with '{}'", entry.getKey(), entry.getValue());
        }
    }

    // ✅ For each placeholder, find matching label in paragraph
    for (Map.Entry<String, String> entry : mappedFields.entrySet()) {
        String placeholder = entry.getKey()   // e.g. [NAMA_PEKERJA]
            .replace("[", "")
            .replace("]", "")
            .replace("_", " ")               // → NAMA PEKERJA
            .toUpperCase();

        String value = entry.getValue() != null ? entry.getValue() : "";

        String normalizedParagraph = fullText.toUpperCase().replaceAll("\\.", "");
        String normalizedPlaceholder = placeholder.replaceAll("\\.", "");

        if (normalizedParagraph.contains(normalizedPlaceholder) && !fullText.contains(entry.getKey())) {
            int colonIndex = fullText.indexOf(":");
            if (colonIndex != -1) {
                updatedText = fullText.substring(0, colonIndex + 1) + " " + value;
                log.info("Replaced label '{}' with value '{}'", placeholder, value);
            }
        }
    }

    // ✅ Set updated text into first run, clear others
    List<XWPFRun> runs = para.getRuns();
    if (!runs.isEmpty() && !updatedText.equals(fullText)) {
        runs.get(0).setText(updatedText, 0);
        for (int i = 1; i < runs.size(); i++) {
            runs.get(i).setText("", 0);
        }
    }
}  
    
    private void generatePdf(String docxPath, String pdfPath) throws Exception {
    String os = System.getProperty("os.name").toLowerCase();
    
    String command;
    if (os.contains("win")) {
        // ✅ Windows (local dev)
        command = "C:\\Program Files\\LibreOffice\\program\\soffice.exe";
    } else {
        // ✅ Linux (production)
        command = "libreoffice";
    }

    ProcessBuilder pb = new ProcessBuilder(
        command, "--headless", "--convert-to", "pdf",
        "--outdir", new File(pdfPath).getParent(),
        docxPath
    );
    pb.redirectErrorStream(true);
    Process process = pb.start();
    String output = new String(process.getInputStream().readAllBytes());
    log.info("LibreOffice output: {}", output);

    int exitCode = process.waitFor();
    if (exitCode != 0) {
        log.error("LibreOffice failed: {}", output);
        throw new RuntimeException("PDF generation failed");
    }
    log.info("Generated PDF saved at {}", pdfPath);
}

public generatedletter getLetterById(UUID letterId) {
    return generatedLetterRepository.findById(letterId)
        .orElseThrow(() -> new RuntimeException("Letter not found: " + letterId));
}
}


