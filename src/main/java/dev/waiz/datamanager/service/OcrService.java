package dev.waiz.datamanager.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.google.cloud.vision.v1.AnnotateImageRequest;
import com.google.cloud.vision.v1.AnnotateImageResponse;
import com.google.cloud.vision.v1.BatchAnnotateImagesResponse;
import com.google.cloud.vision.v1.Feature;
import com.google.cloud.vision.v1.Image;
import com.google.cloud.vision.v1.ImageAnnotatorClient;
import com.google.protobuf.ByteString;

@Service
public class OcrService {

    public String extractText(String filePath) throws IOException {

        byte[] imageByte = Files.readAllBytes(Paths.get(filePath));
        ByteString imgByte = ByteString.copyFrom(imageByte);

        Image img = Image.newBuilder().setContent(imgByte).build();
        Feature feat = Feature.newBuilder()
                       .setType(Feature.Type.TEXT_DETECTION)
                       .build();
        AnnotateImageRequest req = AnnotateImageRequest.newBuilder()
                       .addFeatures(feat)
                       .setImage(img)
                       .build();
                       
        List<AnnotateImageRequest> requests = new ArrayList<>();
        requests.add(req);
        
        
        try(ImageAnnotatorClient client = ImageAnnotatorClient.create()) {
            BatchAnnotateImagesResponse response = client.batchAnnotateImages(requests);
            List<AnnotateImageResponse> responses = response.getResponsesList();

            StringBuilder extracttext = new StringBuilder();
            for (AnnotateImageResponse res: responses){
                if(res.hasError()){
                    throw new IOException("Vision API erorr:"+ res.getError().getMessage());
                }
                extracttext.append(res.getFullTextAnnotation().getText());
            }
            return extracttext.toString();

            
        } 
    }
}
