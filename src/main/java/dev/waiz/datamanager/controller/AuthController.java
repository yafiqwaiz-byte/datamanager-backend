package dev.waiz.datamanager.controller;

import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.waiz.datamanager.model.account;
import dev.waiz.datamanager.model.refreshtoken;
import dev.waiz.datamanager.service.RefreshTokenService;
import dev.waiz.datamanager.service.accountservice;
import dev.waiz.datamanager.util.CookieUtil;
import dev.waiz.datamanager.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
@CrossOrigin(origins = "http://localhost:3000",allowCredentials = "true")
public class AuthController {

    private final RefreshTokenService refreshTokenService;
    private final accountservice accountService;
    private final JwtUtil jwtUtil;
    private final CookieUtil cookieUtil;

    

     // ──────────────────────────────────────────────────────────────────
    //  POST /api/auth/refresh
    //  Reads the refresh token from the httpOnly cookie, validates it,
    //  rotates it, and sets fresh access + refresh cookies.
    //  Frontend just calls this endpoint — no token handling required.
    // ──────────────────────────────────────────────────────────────────
    @PostMapping("/refresh")
    public ResponseEntity<?> refreshAuth(HttpServletRequest request,HttpServletResponse response){
        Optional<String> refreshTokenOpt = cookieUtil.readCookie(request, CookieUtil.REFRESH_TOKEN_COOKIE);

        if (refreshTokenOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(Map.of("error","No refresh token"));
        }

        Optional<refreshtoken> rotated = refreshTokenService.rotateRefreshToken(refreshTokenOpt.get());

        if (rotated.isEmpty()) {
            
            cookieUtil.clearAuthCookies(response);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(Map.of("error","Refresh token invalid or expired,Please try again"));
        }

        refreshtoken newRefreshtoken = rotated.get();
        account acc = newRefreshtoken.getAccount();

        String newAccessToken = jwtUtil.generateToken(acc.getUsername(),acc.getRole());
        String newRefreshToken = newRefreshtoken.getToken();

        cookieUtil.addAuthCookies(response, newAccessToken, newRefreshToken);

        return ResponseEntity.ok(Map.of("username",acc.getUsername(),"role",acc.getRole()));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request,HttpServletResponse response){

        //Get logged in username from security context(set by JWTAuthFilter)
        String username = SecurityContextHolder.getContext()
        .getAuthentication()
        .getName();

        accountService.getAccountByUsername(username).ifPresent(acc -> {
            refreshTokenService.revokeAllTokensForAccount(acc);
        });

        cookieUtil.clearAuthCookies(response);

        SecurityContextHolder.clearContext();

        return ResponseEntity.ok(Map.of("Message","Logged out successfully"));
    }
}
