package dev.waiz.datamanager.controller;

import dev.waiz.datamanager.model.account;
import dev.waiz.datamanager.service.accountservice;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/accounts")
public class accountcontroller {

    @Autowired
    private accountservice accountService;

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
        // Note: In production, validate the old password and hash the new password
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
