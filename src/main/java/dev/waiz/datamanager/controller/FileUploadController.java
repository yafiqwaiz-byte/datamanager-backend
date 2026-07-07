package dev.waiz.datamanager.controller;


import java.util.Map;

import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.annotation.PostMapping;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;


import dev.waiz.datamanager.model.ocrresult;
import dev.waiz.datamanager.service.FileUploadService;
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

   

@PostMapping(value = "/forms/image/upload", consumes = "multipart/form-data")
public  ResponseEntity<?> uploadFormImage(@RequestParam("file") MultipartFile file){
    try{
        String filepath = fileUploadService.saveFormImage(file);
        return ResponseEntity.ok(Map.of("path",filepath));
    } catch (Exception e){
        return ResponseEntity.status(500)
        .body(Map.of("error","Image upload failed:"+ e.getMessage()));
    }
 }
    


}
