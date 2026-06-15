package dev.waiz.datamanager.controller;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import dev.waiz.datamanager.model.fileupload;
import dev.waiz.datamanager.model.ocrresult;
import dev.waiz.datamanager.service.FileUploadService;
import dev.waiz.datamanager.util.ByteArrayMultipartFile;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/files")
public class FileUploadController {

    
    private final FileUploadService fileUploadService;

    @PostMapping(value = "/ocr/upload",consumes = "multipart/form-data")
    public ResponseEntity<?> uploadAndProcess(@RequestParam("file") MultipartFile file){
        try {
            ocrresult result = fileUploadService.uploadAndProcess(file);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("OCR failed:" + e.getMessage());
        }
    }

    @GetMapping("/my-uploads")
    public ResponseEntity<List<fileupload>> getMyUploads(){
        return ResponseEntity.ok(fileUploadService.getUserUploads());
    }

   @PostMapping(value = "/excel/upload", consumes = "application/octet-stream")
public ResponseEntity<?> uploadExcel(
        HttpServletRequest request,
        @RequestHeader("X-File-Name") String fileName) {
    try {
        byte[] bytes = request.getInputStream().readAllBytes();
        String originalName = URLDecoder.decode(fileName, StandardCharsets.UTF_8);

        // No MockMultipartFile — uses our own production-safe implementation
        MultipartFile file = new ByteArrayMultipartFile(
            bytes, "file", originalName, "application/octet-stream"
        );

        fileupload upload = fileUploadService.saveExcelUpload(file);
        return ResponseEntity.ok(upload);
    } catch (Exception e) {
        return ResponseEntity.status(500).body("Excel file upload failed: " + e.getMessage());
    }
}
    


}
