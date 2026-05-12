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

    private static final String GENERATED_DIR = "uploads/generated_letters/";

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
                                String outputPath) throws Exception {

        try (FileInputStream fis = new FileInputStream(templatePath);
             XWPFDocument doc = new XWPFDocument(fis)) {
            
                for (XWPFTable table :doc.getTables()){
                    for (XWPFTableRow row : table.getRows()){
                        for (XWPFTableCell cell : row.getTableCells()){
                            for (XWPFParagraph para : cell.getParagraphs()){
                                replaceParagraph(para,mappedFields);
                            }
                        }
                    }
                }

            try (FileOutputStream fos = new FileOutputStream(outputPath)) {
                doc.write(fos);
            }
            log.info("Generated DOCX saved at {}", outputPath);
        }
    }

    private void replaceParagraph(XWPFParagraph para,
                                    Map<String,String> mappedFields){

            for (XWPFRun run : para.getRuns()){
                String text = run.getText(0);
                if (text == null) continue;

                for (Map.Entry<String,String> entry :mappedFields.entrySet()){
                    if (text.contains(entry.getKey())) {
                        text = text.replace(entry.getKey(), entry.getValue());
                        
                    }
                }
                run.setText(text,0);
                    
            }
    }  
    
    private void generatePdf(String docxPath, String pdfPath) throws Exception {
        // Placeholder for DOCX to PDF conversion logic
        // This can be implemented using libraries like Apache PDFBox or iText
        // For simplicity, we will just copy the DOCX file to the PDF path
       ProcessBuilder pb = new ProcessBuilder(
                "libreoffice", "--headless", "--convert-to", "pdf",
                "--outdir", new File(pdfPath).getParent(),
                docxPath
        );
        pb.redirectErrorStream(true);
        Process process = pb.start();
        int exitCode = process.waitFor();

        if (exitCode != 0) {
           log.error("LibreOffice PDF conversion failed with exit code: {}",exitCode);
           throw new RuntimeException("Failed to convert DOCX to PDF");
        }
        log.info("Generated PDF saved at {}", pdfPath);
    }

}


