package dev.waiz.datamanager.controller;

import dev.waiz.datamanager.model.account;
import dev.waiz.datamanager.model.user;
import dev.waiz.datamanager.model.staff;
import dev.waiz.datamanager.model.refreshtoken;
import dev.waiz.datamanager.service.accountservice;
import dev.waiz.datamanager.service.userservice;
import dev.waiz.datamanager.service.staffservice;
import dev.waiz.datamanager.service.LoginAttemptService;
import dev.waiz.datamanager.service.RefreshTokenService;
import dev.waiz.datamanager.service.StaffInviteService;
import dev.waiz.datamanager.util.CookieUtil;
import dev.waiz.datamanager.util.JwtUtil;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import dev.waiz.datamanager.dto.SigninRequest;
import dev.waiz.datamanager.dto.SignupUserRequest;
import dev.waiz.datamanager.dto.SignupStaffRequest;
import dev.waiz.datamanager.dto.AuthResponse;
import dev.waiz.datamanager.dto.GoogleSignInRequest;
import dev.waiz.datamanager.dto.CompleteUserProfileRequest;
import dev.waiz.datamanager.dto.CompleteStaffProfileRequest;
import dev.waiz.datamanager.dto.ForgotPasswordRequest;
import dev.waiz.datamanager.service.EmailService;
import dev.waiz.datamanager.service.GoogleAuthService;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/accounts")
@CrossOrigin(origins = "http://localhost:3000", allowCredentials = "true")
public class accountcontroller {

     private final accountservice accountService;
     private final userservice         userService;
     private final staffservice        staffService;
     private final JwtUtil             jwtUtil;
     private final GoogleAuthService   googleAuthService;
     private final LoginAttemptService loginAttemptService;
     private final RefreshTokenService refreshTokenService;
     private final CookieUtil          cookieUtil;
     private final StaffInviteService staffInviteService;
     private final EmailService emailService;

    // ──────────────────────────────────────────────────────────────────
    //  SIGNIN
    // ──────────────────────────────────────────────────────────────────
    @Transactional
    @PostMapping("/signin")
    public ResponseEntity<?> signin(@RequestBody SigninRequest signinRequest,
                                    HttpServletRequest request,
                                    HttpServletResponse response) {

        String clientIp = getClientIp(request);

        // ① Rate-limit check
        if (loginAttemptService.isBlocked(clientIp)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body("Too many failed login attempts. Please try again in 15 minutes.");
        }

        // ② Verify credentials
        boolean isValid = accountService.verifyCredentials(
                signinRequest.getUsername(), signinRequest.getPassword());

        if (!isValid) {
            loginAttemptService.loginFailed(clientIp);
            int remaining = loginAttemptService.getRemainingAttempts(clientIp);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Invalid credentials. " + remaining + " attempt(s) remaining.");
        }

        // ③ Load account
        Optional<account> accountOpt = accountService.getAccountByUsername(signinRequest.getUsername());
        if (accountOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Account not found");
        }

        account acc = accountOpt.get();

        //Block pending accounts
        if ("pending".equals(acc.getStatus())){
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Account pending approval. You will receive an email once approved.");
        }

        if ("inactive".equals(acc.getStatus())){
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Account is has been deactivated. Please contact Admin support.");
        }

        // ④ Reset rate-limit counter on success
        loginAttemptService.loginSucceeded(clientIp);

        // ⑤ Issue tokens
        String accessToken        = jwtUtil.generateToken(acc.getUsername(), acc.getRole());
        refreshtoken refreshToken = refreshTokenService.createRefreshToken(acc);

        // ⑥ Set httpOnly cookies
        cookieUtil.addAuthCookies(response, accessToken, refreshToken.getToken());

        // ⑦ Fetch role-specific profile
        Object userDetails = getRoleDetails(acc);

        // ⑧ Return response — token intentionally omitted from body
        AuthResponse authResponse = AuthResponse.builder()
                .username(acc.getUsername())
                .role(acc.getRole())
                .user(userDetails != null ? userDetails : acc)
                .build();

        return ResponseEntity.ok(authResponse);
    }

    // ──────────────────────────────────────────────────────────────────
    //  SIGNUP USER
    // ──────────────────────────────────────────────────────────────────
    @Transactional
    @PostMapping("/signup/user")
    public ResponseEntity<?> signupUser(@RequestBody SignupUserRequest signupRequest,
                                        HttpServletResponse response) {

        if (accountService.usernameExists(signupRequest.getUsername())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Username already exists");
        }
        if (userService.companyNameExists(signupRequest.getCompanyName())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Company name already exists");
        }

        try {
            account newAccount = new account(
                    signupRequest.getUsername(),
                    signupRequest.getPassword(),
                    "USER",
                    "active"
            );
            newAccount.setSecurityQuestion(signupRequest.getSecurityQuestion());
            newAccount.setSecurityAnswer(signupRequest.getSecurityAnswer());
            account createdAccount = accountService.createAccount(newAccount);

            user newUser = new user(
                    createdAccount,
                    signupRequest.getFullName(),
                    signupRequest.getCompanyName(),
                    signupRequest.getPhoneNo(),
                    signupRequest.getCompanyAddress()
            );
            user createdUser = userService.createUser(newUser);

            String accessToken        = jwtUtil.generateToken(createdAccount.getUsername(), createdAccount.getRole());
            refreshtoken refreshToken = refreshTokenService.createRefreshToken(createdAccount);
            cookieUtil.addAuthCookies(response, accessToken, refreshToken.getToken());

            AuthResponse authResponse = AuthResponse.builder()
                    .username(createdAccount.getUsername())
                    .role(createdAccount.getRole())
                    .user(createdUser)
                    .build();

            return ResponseEntity.status(HttpStatus.CREATED).body(authResponse);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error creating user: " + e.getMessage());
        }
    }

    // ──────────────────────────────────────────────────────────────────
    //  SIGNUP STAFF
    // ──────────────────────────────────────────────────────────────────
    @Transactional
    @PostMapping("/signup/staff")
    public ResponseEntity<?> signupStaff(@RequestBody SignupStaffRequest signupRequest,
                                         HttpServletResponse response) {
        
        if(signupRequest.getInviteCode() == null || signupRequest.getInviteCode().isBlank()){
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Staff registration requires a valid invite code. Please contact your admin.");
        }

        if(!staffInviteService.isValidInviteCode(signupRequest.getInviteCode())){
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Invalid or expired invite code. Please contact your admin for a new code.");
        }

        if (accountService.usernameExists(signupRequest.getUsername())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Username already exists");
        }

        try {
            account newAccount = new account(
                    signupRequest.getUsername(),
                    signupRequest.getPassword(),
                    "STAFF",
                    "pending" // Staff accounts start in pending status until approved by an admin
            );
            newAccount.setSecurityQuestion(signupRequest.getSecurityQuestion());
            newAccount.setSecurityAnswer(signupRequest.getSecurityAnswer());
            account createdAccount = accountService.createAccount(newAccount);

            staff newStaff = new staff();
            newStaff.setAccount(createdAccount);
            newStaff.setFullName(signupRequest.getFullName());
            newStaff.setDepartment(signupRequest.getDepartment());
            newStaff.setPosition(signupRequest.getPosition());
            staffService.createStaff(newStaff);

            // Mark invite code as used
             staffInviteService.markAsUsed(signupRequest.getInviteCode());

            try{
                emailService.sendStaffRegistrationAlert(
                    "yafiqwaiz@gmail.com",
                    signupRequest.getUsername()
                );
            } catch (Exception e) {
                System.out.println("⚠️ Admin notification email failed: " + e.getMessage());
            }
            return ResponseEntity.status(HttpStatus.CREATED).body("Staff account created successfully and is pending approval by an admin. " +
            "You will receive an email notification once your account is approved.");

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error creating staff: " + e.getMessage());
        }
    }

    // ──────────────────────────────────────────────────────────────────
    //  GOOGLE SIGN-IN
    // ──────────────────────────────────────────────────────────────────
    @Transactional
    @PostMapping("/signin/google")
    public ResponseEntity<?> signinWithGoogle(@RequestBody GoogleSignInRequest request,
                                              HttpServletResponse response) {

        GoogleIdToken.Payload payload = googleAuthService.verifyToken(request.getIdToken());
        if (payload == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid Google token");
        }

        String email    = payload.getEmail();
        String fullName = (String) payload.get("name");

        Optional<account> existingAccount = accountService.getAccountByUsername(email);

        if (existingAccount.isPresent()) {
            // Existing user — sign in and set cookies
            account acc        = existingAccount.get();
            Object userDetails = getRoleDetails(acc);

            String accessToken        = jwtUtil.generateToken(acc.getUsername(), acc.getRole());
            refreshtoken refreshToken = refreshTokenService.createRefreshToken(acc);
            cookieUtil.addAuthCookies(response, accessToken, refreshToken.getToken());

            AuthResponse authResponse = AuthResponse.builder()
                    .username(acc.getUsername())
                    .role(acc.getRole())
                    .user(userDetails != null ? userDetails : acc)
                    .newUser(false)
                    .build();

            return ResponseEntity.ok(authResponse);

        } else {
            // New Google user — create account, set temp cookie so they can complete profile
            String role = request.getRole() != null ? request.getRole() : "USER";

            account newAccount = new account(email, "", role, "active");
            account createdAccount = accountService.createGoogleAccount(newAccount);

            String accessToken        = jwtUtil.generateToken(createdAccount.getUsername(), createdAccount.getRole());
            refreshtoken refreshToken = refreshTokenService.createRefreshToken(createdAccount);
            cookieUtil.addAuthCookies(response, accessToken, refreshToken.getToken());

            AuthResponse authResponse = AuthResponse.builder()
                    .username(createdAccount.getUsername())
                    .role(createdAccount.getRole())
                    .user(null)
                    .newUser(true)
                    .fullName(fullName)
                    .email(email)
                    .build();

            return ResponseEntity.ok(authResponse);
        }
    }

    // ──────────────────────────────────────────────────────────────────
    //  COMPLETE PROFILE — Google user
    // ──────────────────────────────────────────────────────────────────
    @Transactional
    @PostMapping("/complete-profile/user")
    public ResponseEntity<?> completeUserProfile(@RequestBody CompleteUserProfileRequest request,
                                                 HttpServletRequest httpRequest) {
        try {
            // Read username from cookie-based token (set by JwtAuthFilter in SecurityContext)
            String username = extractUsernameFromRequest(httpRequest);
            if (username == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Not authenticated");
            }

            Optional<account> accountOpt = accountService.getAccountByUsername(username);
            if (accountOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Account not found");
            }

            account acc = accountOpt.get();

            Optional<user> existingUser = userService.getUserByAccountId(acc.getAccountId());
            if (existingUser.isPresent()) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body("Profile already completed");
            }

            user newUser = new user(
                    acc,
                    acc.getUsername().contains("@") ? acc.getUsername().split("@")[0] : acc.getUsername(),
                    request.getCompanyName(),
                    request.getPhoneNo(),
                    request.getCompanyAddress()
            );
            user createdUser = userService.createUser(newUser);

            AuthResponse authResponse = AuthResponse.builder()
                    .username(acc.getUsername())
                    .role(acc.getRole())
                    .user(createdUser)
                    .newUser(false)
                    .build();

            return ResponseEntity.ok(authResponse);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error completing profile: " + e.getMessage());
        }
    }

    // ──────────────────────────────────────────────────────────────────
    //  COMPLETE PROFILE — Google staff
    // ──────────────────────────────────────────────────────────────────
    @Transactional
    @PostMapping("/complete-profile/staff")
    public ResponseEntity<?> completeStaffProfile(@RequestBody CompleteStaffProfileRequest request,
                                                  HttpServletRequest httpRequest) {
        try {
            String username = extractUsernameFromRequest(httpRequest);
            if (username == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Not authenticated");
            }

            Optional<account> accountOpt = accountService.getAccountByUsername(username);
            if (accountOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Account not found");
            }

            account acc = accountOpt.get();

            Optional<staff> existingStaff = staffService.getStaffByAccountId(acc.getAccountId());
            if (existingStaff.isPresent()) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body("Profile already completed");
            }

            staff newStaff = new staff();
            newStaff.setAccount(acc);
            newStaff.setFullName(acc.getUsername().contains("@") ? acc.getUsername().split("@")[0] : acc.getUsername());
            newStaff.setDepartment(request.getDepartment());
            newStaff.setPosition(request.getPosition());
            staff createdStaff = staffService.createStaff(newStaff);

            AuthResponse authResponse = AuthResponse.builder()
                    .username(acc.getUsername())
                    .role(acc.getRole())
                    .user(createdStaff)
                    .newUser(false)
                    .build();

            return ResponseEntity.ok(authResponse);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error completing profile: " + e.getMessage());
        }
    }

    // ──────────────────────────────────────────────────────────────────
    //  ACCOUNT CRUD
    // ──────────────────────────────────────────────────────────────────

    @Transactional
    @PostMapping
    public ResponseEntity<?> createAccount(@RequestBody account newAccount) {
        if (accountService.usernameExists(newAccount.getUsername())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Username already exists");
        }
        account createdAccount = accountService.createAccount(newAccount);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdAccount);
    }

    @Transactional
    @GetMapping
    public ResponseEntity<List<account>> getAllAccounts() {
        return ResponseEntity.ok(accountService.getAllAccounts());
    }

    @Transactional
    @GetMapping("/{accountId}")
    public ResponseEntity<?> getAccountById(@PathVariable UUID accountId) {
        Optional<account> foundAccount = accountService.getAccountById(accountId);
        if (foundAccount.isPresent()) return ResponseEntity.ok(foundAccount.get());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Account not found");
    }

    @Transactional
    @PutMapping("/{accountId}")
    public ResponseEntity<?> updateAccount(@PathVariable UUID accountId,
                                           @RequestBody account updatedAccount) {
        account updated = accountService.updateAccount(accountId, updatedAccount);
        if (updated != null) return ResponseEntity.ok(updated);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Account not found");
    }

    @DeleteMapping("/{accountId}")
    public ResponseEntity<?> deleteAccount(@PathVariable UUID accountId) {
        boolean deleted = accountService.deleteAccount(accountId);
        if (deleted) return ResponseEntity.noContent().build();
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Account not found");
    }

    @Transactional
    @PatchMapping("/{accountId}/status")
    public ResponseEntity<?> updateAccountStatus(@PathVariable UUID accountId,
                                                 @RequestParam String status) {
        account updated = accountService.updateAccountStatus(accountId, status);
        if (updated != null) return ResponseEntity.ok(updated);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Account not found");
    }

    @PatchMapping("/{accountId}/password")
    public ResponseEntity<?> updateAccountPassword(@PathVariable UUID accountId,
                                                   @RequestBody PasswordChangeRequest passwordRequest) {
        account updated = accountService.updateAccountPassword(accountId, passwordRequest.newPassword);
        if (updated != null) return ResponseEntity.ok("Password updated successfully");
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Account not found");
    }

    @Transactional
    @PostMapping("/verify")
    public ResponseEntity<?> verifyCredentials(@RequestBody LoginRequest loginRequest) {
        boolean isValid = accountService.verifyCredentials(loginRequest.username, loginRequest.password);
        if (isValid) {
            Optional<account> foundAccount = accountService.getAccountByUsername(loginRequest.username);
            return ResponseEntity.ok(foundAccount.get());
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid username or password");
    }

    @GetMapping("/security-question/{username}")
    public ResponseEntity<?> getSecurityQuestion(@PathVariable String username) {
        try {
            String question = accountService.getSecurityQuestion(username);
            return ResponseEntity.ok(Map.of("securityQuestion", question));
        } catch (RuntimeException e) {
            return ResponseEntity.status(404).body(e.getMessage());
        }
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody ForgotPasswordRequest req) {
        try {
            accountService.resetPassword(req);
            return ResponseEntity.ok(Map.of("message", "Password reset successfully"));
        } catch (RuntimeException e) {
            return ResponseEntity.status(404).body(e.getMessage());
        }
    }

    // ──────────────────────────────────────────────────────────────────
    //  Private helpers
    // ──────────────────────────────────────────────────────────────────

    /** Fetches user or staff profile based on account role */
    private Object getRoleDetails(account acc) {
        if ("USER".equals(acc.getRole())) {
            return userService.getUserByAccountId(acc.getAccountId()).orElse(null);
        } else if ("STAFF".equals(acc.getRole())) {
            return staffService.getStaffByAccountId(acc.getAccountId()).orElse(null);
        }
        return null;
    }

    /**
     * Extracts the username from either:
     *  - httpOnly cookie token (preferred, set by JwtAuthFilter)
     *  - Authorization: Bearer header (fallback for Postman)
     */
    private String extractUsernameFromRequest(HttpServletRequest request) {
        // Try cookie first
        Optional<String> cookieToken = cookieUtil.readCookie(request, CookieUtil.ACCESS_TOKEN_COOKIE);
        if (cookieToken.isPresent() && jwtUtil.validateToken(cookieToken.get())) {
            return jwtUtil.extractUsername(cookieToken.get());
        }
        // Fallback: Bearer header
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            if (jwtUtil.validateToken(token)) return jwtUtil.extractUsername(token);
        }
        return null;
    }

    /** Resolves the real client IP, handling reverse proxies */
    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    // ──────────────────────────────────────────────────────────────────
    //  Helper DTOs
    // ──────────────────────────────────────────────────────────────────

    public static class PasswordChangeRequest {
        public String oldPassword;
        public String newPassword;
    }

    public static class LoginRequest {
        public String username;
        public String password;
    }
}