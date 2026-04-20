package dev.waiz.datamanager.service;

import dev.waiz.datamanager.model.user;
import dev.waiz.datamanager.repository.userrepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class userservice {

    @Autowired
    private userrepository userRepository;

    // CREATE - Save a new user
    public user createUser(user newUser) {
        return userRepository.save(newUser);
    }

    // READ - Get all users
    public List<user> getAllUsers() {
        return userRepository.findAll();
    }

    // READ - Get user by ID
    public Optional<user> getUserById(UUID userId) {
        return userRepository.findById(userId);
    }

    // READ - Get user by full name
    public Optional<user> getUserByFullName(String fullName) {
        return userRepository.findByFullName(fullName);
    }

    // READ - Get user by company name
    public Optional<user> getUserByCompanyName(String companyName) {
        return userRepository.findByCompanyName(companyName);
    }

    // READ - Get user by phone number
    public Optional<user> getUserByPhoneNo(String phoneNo) {
        return userRepository.findByPhoneNo(phoneNo);
    }

    // READ - Get user by account ID
    public Optional<user> getUserByAccountId(UUID accountId) {
        return userRepository.findByAccount_AccountId(accountId);
    }

    // READ - Search users by company name (partial match)
    public List<user> searchUsersByCompanyName(String companyName) {
        return userRepository.findByCompanyNameContainingIgnoreCase(companyName);
    }

    // UPDATE - Update user details
    public user updateUser(UUID userId, user updatedUser) {
        return userRepository.findById(userId)
                .map(existingUser -> {
                    // Update fields
                    if (updatedUser.getFullName() != null) {
                        existingUser.setFullName(updatedUser.getFullName());
                    }
                    if (updatedUser.getCompanyName() != null) {
                        existingUser.setCompanyName(updatedUser.getCompanyName());
                    }
                    if (updatedUser.getPhoneNo() != null) {
                        existingUser.setPhoneNo(updatedUser.getPhoneNo());
                    }
                    if (updatedUser.getCompanyAddress() != null) {
                        existingUser.setCompanyAddress(updatedUser.getCompanyAddress());
                    }
                    return userRepository.save(existingUser);
                })
                .orElse(null);
    }

    // UPDATE - Update user phone number
    public user updateUserPhoneNo(UUID userId, String phoneNo) {
        return userRepository.findById(userId)
                .map(existingUser -> {
                    existingUser.setPhoneNo(phoneNo);
                    return userRepository.save(existingUser);
                })
                .orElse(null);
    }

    // UPDATE - Update user company address
    public user updateUserCompanyAddress(UUID userId, String companyAddress) {
        return userRepository.findById(userId)
                .map(existingUser -> {
                    existingUser.setCompanyAddress(companyAddress);
                    return userRepository.save(existingUser);
                })
                .orElse(null);
    }

    // UPDATE - Update user company name
    public user updateUserCompanyName(UUID userId, String companyName) {
        return userRepository.findById(userId)
                .map(existingUser -> {
                    existingUser.setCompanyName(companyName);
                    return userRepository.save(existingUser);
                })
                .orElse(null);
    }

    // DELETE - Delete user by ID
    public boolean deleteUser(UUID userId) {
        if (userRepository.existsById(userId)) {
            userRepository.deleteById(userId);
            return true;
        }
        return false;
    }

    // CHECK - Verify if company name exists
    public boolean companyNameExists(String companyName) {
        return userRepository.existsByCompanyName(companyName);
    }

    // CHECK - Verify if phone number exists
    public boolean phoneNoExists(String phoneNo) {
        return userRepository.existsByPhoneNo(phoneNo);
    }

    // COUNT - Get total number of users
    public long getTotalUserCount() {
        return userRepository.count();
    }
}
