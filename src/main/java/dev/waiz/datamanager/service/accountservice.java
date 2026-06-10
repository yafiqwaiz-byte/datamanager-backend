package dev.waiz.datamanager.service;

import dev.waiz.datamanager.dto.ForgotPasswordRequest;
import dev.waiz.datamanager.model.account;
import dev.waiz.datamanager.repository.accountrepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;
import java.util.UUID;


@Service
public class accountservice {

    @Autowired
    private accountrepository accountRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // CREATE - Save a new account
    public account createAccount(account newAccount) {
        // Hash the password before saving
        if (newAccount.getPasswordHash() != null && !newAccount.getPasswordHash().isEmpty()) {
            String hashedPassword = passwordEncoder.encode(newAccount.getPasswordHash());
            newAccount.setPasswordHash(hashedPassword);
        }
        return accountRepository.save(newAccount);
    }

    public String getSecurityQuestion(String usename){
        account acc = accountRepository.findByUsername(usename)
        .orElseThrow(() -> new RuntimeException("User not found"));
        if (acc.getSecurityQuestion() == null){
            throw new RuntimeException("No security question for this account");
        }
        return acc.getSecurityQuestion();
    }

    public void resetPassword(ForgotPasswordRequest req){
        account acc = accountRepository.findByUsername(req.getUsername())
        .orElseThrow(() -> new RuntimeException("User not found"));

        if (!acc.getSecurityAnswer().equalsIgnoreCase(req.getSecurityAnswer())){
            throw new RuntimeException("Incorrect security answer");
        }

        acc.setPasswordHash(passwordEncoder.encode(req.getNewPassword()));
        accountRepository.save(acc);
    }

    // Google accounts don't have passwords - skip hashing
    public account createGoogleAccount(account newAccount) {
        return accountRepository.save(newAccount);
    }


    // READ - Get all accounts
    public List<account> getAllAccounts() {
        return accountRepository.findAll();
    }

    // READ - Get account by ID
    public Optional<account> getAccountById(UUID accountId) {
        return accountRepository.findById(accountId);
    }

    // READ - Get account by username
    public Optional<account> getAccountByUsername(String username) {
        return accountRepository.findByUsername(username);
    }

    // UPDATE - Update account details
    public account updateAccount(UUID accountId, account updatedAccount) {
        return accountRepository.findById(accountId)
                .map(existingAccount -> {
                    // Update fields
                    if (updatedAccount.getUsername() != null) {
                        existingAccount.setUsername(updatedAccount.getUsername());
                    }
                    if (updatedAccount.getPasswordHash() != null) {
                        existingAccount.setPasswordHash(updatedAccount.getPasswordHash());
                    }
                    if (updatedAccount.getRole() != null) {
                        existingAccount.setRole(updatedAccount.getRole());
                    }
                    if (updatedAccount.getStatus() != null) {
                        existingAccount.setStatus(updatedAccount.getStatus());
                    }
                    return accountRepository.save(existingAccount);
                })
                .orElse(null);
    }

    // DELETE - Delete account by ID
    public boolean deleteAccount(UUID accountId) {
        if (accountRepository.existsById(accountId)) {
            accountRepository.deleteById(accountId);
            return true;
        }
        return false;
    }

    // UPDATE - Change account status
    public account updateAccountStatus(UUID accountId, String status) {
        return accountRepository.findById(accountId)
                .map(existingAccount -> {
                    existingAccount.setStatus(status);
                    return accountRepository.save(existingAccount);
                })
                .orElse(null);
    }

    // UPDATE - Change account password (hash the password)
    public account updateAccountPassword(UUID accountId, String plainPassword) {
        return accountRepository.findById(accountId)
                .map(existingAccount -> {
                    String hashedPassword = passwordEncoder.encode(plainPassword);
                    existingAccount.setPasswordHash(hashedPassword);
                    return accountRepository.save(existingAccount);
                })
                .orElse(null);
    }

    // VERIFY - Check if username and password match
    public boolean verifyCredentials(String username, String plainPassword) {
        Optional<account> accountOptional = accountRepository.findByUsername(username);
        if (accountOptional.isPresent()) {
            account existingAccount = accountOptional.get();
            // Compare plain password with hashed password using BCrypt
            return passwordEncoder.matches(plainPassword, existingAccount.getPasswordHash());
        }
        return false;
    }

    // CHECK - Verify if username already exists
    public boolean usernameExists(String username) {
        return accountRepository.existsByUsername(username);
    }

    public List<account> getAccountsByStatus(String status){
        return accountRepository.findByStatus(status);
    }

    public List<account> getAccountsByRole(String role){
        return accountRepository.findByRole(role);
    }
}
