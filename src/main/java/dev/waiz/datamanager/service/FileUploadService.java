package dev.waiz.datamanager.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.multipart.MultipartFile;


import dev.waiz.datamanager.model.account;
import dev.waiz.datamanager.model.fileupload;
import dev.waiz.datamanager.model.processeddata;
import dev.waiz.datamanager.model.user;
import dev.waiz.datamanager.repository.FileUploadRepository;
import dev.waiz.datamanager.repository.ProcessedDataRepository;
import dev.waiz.datamanager.repository.accountrepository;
import dev.waiz.datamanager.repository.userrepository;

public class FileUploadService {

    @Autowired
    private FileUploadRepository fileUploadRepository;

    @Autowired
    private ProcessedDataRepository processedDataRepository;

    @Autowired
    private OcrService ocrService;

    @Autowired
    private userrepository userrepository;

    @Autowired
    private accountrepository accountrepository;

    private final String uploadDir = "uploads/ocr-files/";

    public processeddata uploadAndProcess(MultipartFile file) throws IOException{

        String username = SecurityContextHolder.getContext()
                          .getAuthentication().getName();
        account acc = accountrepository.findByUsername(username)
                      .orElseThrow(() -> new RuntimeException("Account not found"));
        user user = userrepository.findByAccount_AccountId(acc.getAccountId())
                      .orElseThrow(() -> new RuntimeException("User not found")); 
                      
        String originName = file.getOriginalFilename();
        String extension = originName != null && originName.contains(".")   
                           ? originName.substring(originName.lastIndexOf("."))
                           :"";
        String filename = UUID.randomUUID() + extension;
        Path savepath = Paths.get(uploadDir + filename);
        Files.createDirectories(savepath.getParent());
        Files.write(savepath, file.getBytes()); 
        
        fileupload upload = new fileupload();
        upload.setUser(user);
        upload.setFileName(originName);
        upload.setFileType(file.getContentType());
        upload.setFilePath(uploadDir + filename);
        upload.setUploadedAt(LocalDateTime.now());
        fileUploadRepository.save(upload);

        String extractedText = "";
        String errorLog = null;
        String status = "success";
        try {
            extractedText = ocrService.extractText(uploadDir + filename);
        } catch (Exception e) {
            errorLog = e.getMessage();
            status = "failed";
        }

        processeddata processed = new processeddata();
        processed.setUpload(upload);
        processed.setCleanedData(extractedText);
        processed.setValidationStatus(status);
        processed.setErrorLog(errorLog);
        processed.setProcessedAt(LocalDateTime.now());
        processedDataRepository.save(processed);

        return processed;
    }

    public List<fileupload> getUserUploads() {
        String username = SecurityContextHolder.getContext()
                          .getAuthentication().getName();
        account acc = accountrepository.findByUsername(username)
                          .orElseThrow(() -> new RuntimeException("Account not found"));                 
        user user = userrepository.findByAccount_AccountId(acc.getAccountId())
                          .orElseThrow(() -> new RuntimeException("User not found"));
                          return fileUploadRepository.findByUser(user);
                        }
}
