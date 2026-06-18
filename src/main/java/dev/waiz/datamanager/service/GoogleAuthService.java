package dev.waiz.datamanager.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import dev.waiz.datamanager.config.GoogleProperties;
import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import java.util.Collections;
@Service
@RequiredArgsConstructor
public class GoogleAuthService {

    
    private final GoogleProperties googleProperties;

    public GoogleIdToken.Payload verifyToken(String idTokenString) {
        try {
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
                    new NetHttpTransport(),
                    new GsonFactory()
            )
                    .setAudience(Collections.singletonList(googleProperties.getClientId()))
                    .build();

            GoogleIdToken idToken = verifier.verify(idTokenString);
            if (idToken != null) {
                return idToken.getPayload();
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }
}
