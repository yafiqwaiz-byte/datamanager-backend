package dev.waiz.datamanager.repository;

import dev.waiz.datamanager.model.account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface accountrepository extends JpaRepository<account, UUID> {
    
    // Find account by username
    Optional<account> findByUsername(String username);
    
    // Check if username exists
    boolean existsByUsername(String username);

    // Find accounts by role
    List<account> findByRole(String role);
    
    // Find accounts by status
    List<account> findByStatus(String status);
}
