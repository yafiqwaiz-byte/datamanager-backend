package dev.waiz.datamanager.service;


import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;


import dev.waiz.datamanager.model.account;
import dev.waiz.datamanager.model.fileupload;
import dev.waiz.datamanager.model.ocrresult;
import dev.waiz.datamanager.model.staff;
import dev.waiz.datamanager.model.user;
import dev.waiz.datamanager.repository.FileUploadRepository;
import dev.waiz.datamanager.repository.OcrResultRepository;
import dev.waiz.datamanager.repository.accountrepository;
import dev.waiz.datamanager.repository.staffrepository;
import dev.waiz.datamanager.repository.userrepository;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Service
public class FileUploadService {

    
    private final FileUploadRepository fileUploadRepository;

    
    private final OcrResultRepository ocrResultRepository;

    
    private final OcrService ocrService;

    
    private final userrepository userrepository;

    
    private final accountrepository accountrepository;

    
    private final staffrepository staffrepository;

    private final String uploadDir = "uploads/ocr-files/";

    public ocrresult uploadAndProcess(MultipartFile file) throws IOException{

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
        upload.setUploadedAt(OffsetDateTime.now());
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

        ocrresult result = new ocrresult();
        result.setUpload(upload);
        result.setExtractedText(extractedText);
        result.setStatus(status);
        result.setErrorLog(errorLog);
        result.setProcessedAt(OffsetDateTime.now());
        ocrResultRepository.save(result);

        return result;
    }

    public fileupload saveExcelUpload(MultipartFile file) throws IOException{

        String username = SecurityContextHolder.getContext()
                          .getAuthentication().getName();
        account acc = accountrepository.findByUsername(username)
                      .orElseThrow(() -> new RuntimeException("Account not found"));
        staff currentStaff = staffrepository.findByAccount_AccountId(acc.getAccountId()).orElseThrow(() -> new RuntimeException("Staff not found."));

        String originName = file.getOriginalFilename();
        String extension = originName!= null && originName.contains(".") ? originName.substring(originName.lastIndexOf(".")):"";

        String filename = UUID.randomUUID() + extension;
        Path savepath = Paths.get(uploadDir + filename);
        Files.createDirectories(savepath.getParent());
        Files.write(savepath,file.getBytes());

        fileupload upload = new fileupload();
        upload.setStaff(currentStaff);
        upload.setFileName(originName);
        upload.setFileType(file.getContentType());
        upload.setFilePath(uploadDir+filename);
        upload.setUploadedAt(OffsetDateTime.now());

        return fileUploadRepository.save(upload);
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
