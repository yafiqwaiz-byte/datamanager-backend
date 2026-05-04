package dev.waiz.datamanager.controller;

import dev.waiz.datamanager.model.account;
import dev.waiz.datamanager.model.user;
import dev.waiz.datamanager.model.staff;
import dev.waiz.datamanager.service.accountservice;
import dev.waiz.datamanager.service.userservice;
import dev.waiz.datamanager.service.staffservice;
import dev.waiz.datamanager.util.JwtUtil;
import dev.waiz.datamanager.dto.SigninRequest;
import dev.waiz.datamanager.dto.SignupUserRequest;
import dev.waiz.datamanager.dto.SignupStaffRequest;
import dev.waiz.datamanager.dto.AuthResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import dev.waiz.datamanager.service.GoogleAuthService;
import dev.waiz.datamanager.dto.GoogleSignInRequest;
import dev.waiz.datamanager.dto.CompleteUserProfileRequest;
import dev.waiz.datamanager.dto.ForgotPasswordRequest;
import dev.waiz.datamanager.dto.CompleteStaffProfileRequest;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
@RestController
@RequestMapping("/api/accounts")
@CrossOrigin(origins = "http://localhost:3000") // Allow CORS for all origins (adjust as needed)
public class accountcontroller {

    @Autowired
    private accountservice accountService;

    @Autowired
    private userservice userService;

    @Autowired
    private staffservice staffService;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private GoogleAuthService googleAuthService;

    // SIGNIN - Authenticate user and return JWT token
    @PostMapping("/signin")
    public ResponseEntity<?> signin(@RequestBody SigninRequest signinRequest) {
        // Verify credentials
        boolean isValid = accountService.verifyCredentials(signinRequest.getUsername(), signinRequest.getPassword());
        if (!isValid) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid username or password");
        }

        // Get account details
        Optional<account> accountOpt = accountService.getAccountByUsername(signinRequest.getUsername());
        if (!accountOpt.isPresent()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Account not found");
        }

        account acc = accountOpt.get();
        
        // Generate JWT token
        String token = jwtUtil.generateToken(acc.getUsername(), acc.getRole());

        // Get user or staff details based on role
        Object userDetails = null;
        if ("USER".equals(acc.getRole())) {
            Optional<user> userOpt = userService.getUserByAccountId(acc.getAccountId());
            userDetails = userOpt.orElse(null);
        } else if ("STAFF".equals(acc.getRole())) {
            Optional<staff> staffOpt = staffService.getStaffByAccountId(acc.getAccountId());
            userDetails = staffOpt.orElse(null);
        }

        // Return auth response with token
        AuthResponse response = AuthResponse.builder()
                .token(token)
                .username(acc.getUsername())
                .role(acc.getRole())
                .user(userDetails != null ? userDetails : acc)
                .build();

        return ResponseEntity.ok(response);
    }

    // SIGNUP USER - Create user account and profile
    @PostMapping("/signup/user")
    public ResponseEntity<?> signupUser(@RequestBody SignupUserRequest signupRequest) {
        // Check if username already exists
        if (accountService.usernameExists(signupRequest.getUsername())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Username already exists");
        }

        // Check if company name already exists
        if (userService.companyNameExists(signupRequest.getCompanyName())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Company name already exists");
        }

        try {
            // Create account
            account newAccount = new account(
                    signupRequest.getUsername(),
                    signupRequest.getPassword(), // Will be hashed in service
                    "USER",
                    "active"
            );
            newAccount.setSecurityQuestion(signupRequest.getSecurityQuestion());
            newAccount.setSecurityAnswer(signupRequest.getSecurityAnswer());
            account createdAccount = accountService.createAccount(newAccount);

            // Create user profile
            user newUser = new user(
                    createdAccount,
                    signupRequest.getFullName(),
                    signupRequest.getCompanyName(),
                    signupRequest.getPhoneNo(),
                    signupRequest.getCompanyAddress()
            );
            user createdUser = userService.createUser(newUser);

            // Generate JWT token
            String token = jwtUtil.generateToken(createdAccount.getUsername(), createdAccount.getRole());

            // Return auth response
            AuthResponse response = AuthResponse.builder()
                    .token(token)
                    .username(createdAccount.getUsername())
                    .role(createdAccount.getRole())
                    .user(createdUser)
                    .build();

            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error creating user: " + e.getMessage());
        }
    }

    // SIGNUP STAFF - Create staff account and profile
    @PostMapping("/signup/staff")
    public ResponseEntity<?> signupStaff(@RequestBody SignupStaffRequest signupRequest) {
        // Check if username already exists
        if (accountService.usernameExists(signupRequest.getUsername())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Username already exists");
        }

        try {
            // Create account
            account newAccount = new account(
                    signupRequest.getUsername(),
                    signupRequest.getPassword(), // Will be hashed in service
                    "STAFF",
                    "active"
            );
            account createdAccount = accountService.createAccount(newAccount);

            // Create staff profile
            staff newStaff = new staff(
                    createdAccount,
                    signupRequest.getFullName(),
                    signupRequest.getDepartment(),
                    signupRequest.getPosition()
            );
            staff createdStaff = staffService.createStaff(newStaff);

            // Generate JWT token
            String token = jwtUtil.generateToken(createdAccount.getUsername(), createdAccount.getRole());

            // Return auth response
            AuthResponse response = AuthResponse.builder()
                    .token(token)
                    .username(createdAccount.getUsername())
                    .role(createdAccount.getRole())
                    .user(createdStaff)
                    .build();

            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error creating staff: " + e.getMessage());
        }
    }

    // CREATE - Create a new account
    @PostMapping
    public ResponseEntity<?> createAccount(@RequestBody account newAccount) {
        // Check if username already exists
        if (accountService.usernameExists(newAccount.getUsername())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Username already exists");
        }
        account createdAccount = accountService.createAccount(newAccount);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdAccount);
    }


    // GOOGLE SIGN-IN
@PostMapping("/signin/google")
public ResponseEntity<?> signinWithGoogle(@RequestBody GoogleSignInRequest request) {
    // Verify Google token
    GoogleIdToken.Payload payload = googleAuthService.verifyToken(request.getIdToken());
    if (payload == null) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid Google token");
    }
 
    String email = payload.getEmail();
    String fullName = (String) payload.get("name");
 
    // Check if account already exists
    Optional<account> existingAccount = accountService.getAccountByUsername(email);
 
    if (existingAccount.isPresent()) {
        // Existing user - sign in normally
        account acc = existingAccount.get();
        Object userDetails = null;
 
        if ("USER".equals(acc.getRole())) {
            Optional<user> userOpt = userService.getUserByAccountId(acc.getAccountId());
            userDetails = userOpt.orElse(null);
        } else if ("STAFF".equals(acc.getRole())) {
            Optional<staff> staffOpt = staffService.getStaffByAccountId(acc.getAccountId());
            userDetails = staffOpt.orElse(null);
        }
 
        String token = jwtUtil.generateToken(acc.getUsername(), acc.getRole());
 
        AuthResponse response = AuthResponse.builder()
                .token(token)
                .username(acc.getUsername())
                .role(acc.getRole())
                .user(userDetails != null ? userDetails : acc)
                .newUser(false)
                .build();
 
        return ResponseEntity.ok(response);
 
    } else {
        // New user - create account only, profile to be completed later
        String role = request.getRole() != null ? request.getRole() : "USER";
 
        account newAccount = new account(
                email,
                "", // no password for Google users
                role,
                "active"
        );
        account createdAccount = accountService.createGoogleAccount(newAccount);
 
        // Generate token so frontend can make authenticated complete-profile call
        String token = jwtUtil.generateToken(createdAccount.getUsername(), createdAccount.getRole());
 
        // Return isNewUser = true so frontend redirects to complete profile page
        AuthResponse response = AuthResponse.builder()
                .token(token)
                .username(createdAccount.getUsername())
                .role(createdAccount.getRole())
                .user(null)
                .newUser(true)
                .fullName(fullName)
                .email(email)
                .build();
 
        return ResponseEntity.ok(response);
    }

}

// COMPLETE PROFILE for Google users
@PostMapping("/complete-profile/user")
public ResponseEntity<?> completeUserProfile(@RequestBody CompleteUserProfileRequest request,
                                              @RequestHeader("Authorization") String authHeader) {
    try {
        String token = authHeader.substring(7);
        String username = jwtUtil.extractUsername(token);
 
        Optional<account> accountOpt = accountService.getAccountByUsername(username);
        if (!accountOpt.isPresent()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Account not found");
        }
 
        account acc = accountOpt.get();
 
        // Check if profile already exists
        Optional<user> existingUser = userService.getUserByAccountId(acc.getAccountId());
        if (existingUser.isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Profile already completed");
        }
 
        // Create user profile with Google name + provided details
        user newUser = new user(
                acc,
                acc.getUsername().contains("@") ? acc.getUsername().split("@")[0] : acc.getUsername(),
                request.getCompanyName(),
                request.getPhoneNo(),
                request.getCompanyAddress()
        );
        user createdUser = userService.createUser(newUser);
 
        AuthResponse response = AuthResponse.builder()
                .token(token)
                .username(acc.getUsername())
                .role(acc.getRole())
                .user(createdUser)
                .newUser(false)
                .build();
 
        return ResponseEntity.ok(response);
    } catch (Exception e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Error completing profile: " + e.getMessage());
    }
}

//COMPLETE PROFILE for Google staff
@PostMapping("/complete-profile/staff")
public ResponseEntity<?> completeStaffProfile(@RequestBody CompleteStaffProfileRequest request,
                                               @RequestHeader("Authorization") String authHeader) {
    try {
        String token = authHeader.substring(7);
        String username = jwtUtil.extractUsername(token);
 
        Optional<account> accountOpt = accountService.getAccountByUsername(username);
        if (!accountOpt.isPresent()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Account not found");
        }
 
        account acc = accountOpt.get();
 
        // Check if profile already exists
        Optional<staff> existingStaff = staffService.getStaffByAccountId(acc.getAccountId());
        if (existingStaff.isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Profile already completed");
        }
 
        // Create staff profile
        staff newStaff = new staff(
                acc,
                acc.getUsername().contains("@") ? acc.getUsername().split("@")[0] : acc.getUsername(),
                request.getDepartment(),
                request.getPosition()
        );
        staff createdStaff = staffService.createStaff(newStaff);
 
        AuthResponse response = AuthResponse.builder()
                .token(token)
                .username(acc.getUsername())
                .role(acc.getRole())
                .user(createdStaff)
                .newUser(false)
                .build();
 
        return ResponseEntity.ok(response);
    } catch (Exception e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Error completing profile: " + e.getMessage());
    }
}
    // READ - Get all accounts
    @GetMapping
    public ResponseEntity<List<account>> getAllAccounts() {
        List<account> accounts = accountService.getAllAccounts();
        return ResponseEntity.ok(accounts);
    }

    // READ - Get account by ID
    @GetMapping("/{accountId}")
    public ResponseEntity<?> getAccountById(@PathVariable UUID accountId) {
        Optional<account> foundAccount = accountService.getAccountById(accountId);
        if (foundAccount.isPresent()) {
            return ResponseEntity.ok(foundAccount.get());
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Account not found");
    }

    // UPDATE - Update account details
    @PutMapping("/{accountId}")
    public ResponseEntity<?> updateAccount(@PathVariable UUID accountId, @RequestBody account updatedAccount) {
        account updated = accountService.updateAccount(accountId, updatedAccount);
        if (updated != null) {
            return ResponseEntity.ok(updated);
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Account not found");
    }

    // DELETE - Delete account
    @DeleteMapping("/{accountId}")
    public ResponseEntity<?> deleteAccount(@PathVariable UUID accountId) {
        boolean deleted = accountService.deleteAccount(accountId);
        if (deleted) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Account not found");
    }

    // UPDATE - Change account status
    @PatchMapping("/{accountId}/status")
    public ResponseEntity<?> updateAccountStatus(@PathVariable UUID accountId, @RequestParam String status) {
        account updated = accountService.updateAccountStatus(accountId, status);
        if (updated != null) {
            return ResponseEntity.ok(updated);
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Account not found");
    }

    // UPDATE - Change account password
    @PatchMapping("/{accountId}/password")
    public ResponseEntity<?> updateAccountPassword(@PathVariable UUID accountId, @RequestBody PasswordChangeRequest passwordRequest) {
        account updated = accountService.updateAccountPassword(accountId, passwordRequest.newPassword);
        if (updated != null) {
            return ResponseEntity.ok("Password updated successfully");
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Account not found");
    }

    // VERIFY - Check username and password (login verification)
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
    public ResponseEntity<?> getSecurityQuestion(@PathVariable String username){
        try {
            String question = accountService.getSecurityQuestion(username);
            return ResponseEntity.ok(Map.of("securityQuestion", question));
        } catch (RuntimeException e) {
            return ResponseEntity.status(404).body(e.getMessage());
        }
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody ForgotPasswordRequest req){
        try{
            accountService.resetPassword(req);
            return ResponseEntity.ok(Map.of("message","Password reset successfully"));
        } catch (RuntimeException e){
            return ResponseEntity.status(404).body(e.getMessage());
        }
    }

    // Helper class for password change
    public static class PasswordChangeRequest {
        public String oldPassword;
        public String newPassword;
    }

    // Helper class for login verification
    public static class LoginRequest {
        public String username;
        public String password;
    }
}
