package dev.waiz.datamanager.controller;

import dev.waiz.datamanager.model.user;
import dev.waiz.datamanager.service.userservice;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
public class usercontroller {

    
    private final userservice userService;

    // CREATE - Create a new user
    @PostMapping
    public ResponseEntity<?> createUser(@RequestBody user newUser) {
        // Check if company name already exists
        if (userService.companyNameExists(newUser.getCompanyName())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Company name already exists");
        }
        // Check if phone number already exists
        if (userService.phoneNoExists(newUser.getPhoneNo())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Phone number already exists");
        }
        user createdUser = userService.createUser(newUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdUser);
    }

    // READ - Get all users
    @GetMapping
    public ResponseEntity<List<user>> getAllUsers() {
        List<user> userList = userService.getAllUsers();
        return ResponseEntity.ok(userList);
    }

    // READ - Get user by ID
    @GetMapping("/{userId}")
    public ResponseEntity<?> getUserById(@PathVariable UUID userId) {
        Optional<user> foundUser = userService.getUserById(userId);
        if (foundUser.isPresent()) {
            return ResponseEntity.ok(foundUser.get());
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("User not found");
    }

    // READ - Get user by full name
    @GetMapping("/search/name")
    public ResponseEntity<?> getUserByName(@RequestParam String fullName) {
        Optional<user> foundUser = userService.getUserByFullName(fullName);
        if (foundUser.isPresent()) {
            return ResponseEntity.ok(foundUser.get());
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("User not found");
    }

    // READ - Get user by company name
    @GetMapping("/search/company")
    public ResponseEntity<?> getUserByCompanyName(@RequestParam String companyName) {
        Optional<user> foundUser = userService.getUserByCompanyName(companyName);
        if (foundUser.isPresent()) {
            return ResponseEntity.ok(foundUser.get());
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("User with this company not found");
    }

    // READ - Get user by phone number
    @GetMapping("/search/phone")
    public ResponseEntity<?> getUserByPhoneNo(@RequestParam String phoneNo) {
        Optional<user> foundUser = userService.getUserByPhoneNo(phoneNo);
        if (foundUser.isPresent()) {
            return ResponseEntity.ok(foundUser.get());
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("User with this phone number not found");
    }

    // READ - Search users by company name (partial match)
    @GetMapping("/search/company-partial")
    public ResponseEntity<?> searchUsersByCompanyName(@RequestParam String companyName) {
        List<user> userList = userService.searchUsersByCompanyName(companyName);
        if (userList.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("No users found with company name containing: " + companyName);
        }
        return ResponseEntity.ok(userList);
    }

    // READ - Get user by account ID
    @GetMapping("/account/{accountId}")
    public ResponseEntity<?> getUserByAccountId(@PathVariable UUID accountId) {
        Optional<user> foundUser = userService.getUserByAccountId(accountId);
        if (foundUser.isPresent()) {
            return ResponseEntity.ok(foundUser.get());
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("User with this account not found");
    }

    // UPDATE - Update user details
    @PutMapping("/{userId}")
    public ResponseEntity<?> updateUser(@PathVariable UUID userId, @RequestBody user updatedUser) {
        user updated = userService.updateUser(userId, updatedUser);
        if (updated != null) {
            return ResponseEntity.ok(updated);
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("User not found");
    }

    // UPDATE - Update user phone number
    @PatchMapping("/{userId}/phone")
    public ResponseEntity<?> updatePhoneNo(@PathVariable UUID userId, @RequestParam String phoneNo) {
        // Check if phone number already exists (for other users)
        if (userService.phoneNoExists(phoneNo)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Phone number already exists");
        }
        user updated = userService.updateUserPhoneNo(userId, phoneNo);
        if (updated != null) {
            return ResponseEntity.ok(updated);
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("User not found");
    }

    // UPDATE - Update user company address
    @PatchMapping("/{userId}/address")
    public ResponseEntity<?> updateCompanyAddress(@PathVariable UUID userId, @RequestParam String companyAddress) {
        user updated = userService.updateUserCompanyAddress(userId, companyAddress);
        if (updated != null) {
            return ResponseEntity.ok(updated);
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("User not found");
    }

    // UPDATE - Update user company name
    @PatchMapping("/{userId}/company")
    public ResponseEntity<?> updateCompanyName(@PathVariable UUID userId, @RequestParam String companyName) {
        // Check if company name already exists (for other users)
        if (userService.companyNameExists(companyName)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Company name already exists");
        }
        user updated = userService.updateUserCompanyName(userId, companyName);
        if (updated != null) {
            return ResponseEntity.ok(updated);
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("User not found");
    }

    // DELETE - Delete user
    @DeleteMapping("/{userId}")
    public ResponseEntity<?> deleteUser(@PathVariable UUID userId) {
        boolean deleted = userService.deleteUser(userId);
        if (deleted) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("User not found");
    }

    // GET - Total user count
    @GetMapping("/stats/total")
    public ResponseEntity<?> getTotalUserCount() {
        long totalCount = userService.getTotalUserCount();
        return ResponseEntity.ok(new UserCountResponse(totalCount));
    }

    // Helper class for user count response
    public static class UserCountResponse {
        public long totalUsers;

        public UserCountResponse(long totalUsers) {
            this.totalUsers = totalUsers;
        }
    }
}
