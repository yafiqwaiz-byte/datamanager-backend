package dev.waiz.datamanager.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import dev.waiz.datamanager.model.fileupload;
import dev.waiz.datamanager.model.processeddata;
import dev.waiz.datamanager.service.FileUploadService;

@RestController
@RequestMapping("/api/ocr")
public class FileUploadController {

    @Autowired
    private FileUploadService fileUploadService;

    @PostMapping(value = "/upload",consumes = "multipart/form-data")
    public ResponseEntity<?> uploadAndProcess(@RequestParam("file") MultipartFile file){
        try {
            processeddata result = fileUploadService.uploadAndProcess(file);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("OCR failed:" + e.getMessage());
        }
    }

    @GetMapping("/my-uploads")
    public ResponseEntity<List<fileupload>> getMyUploads(){
        return ResponseEntity.ok(fileUploadService.getUserUploads());
    }


}
