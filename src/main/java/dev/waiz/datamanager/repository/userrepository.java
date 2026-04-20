package dev.waiz.datamanager.repository;

import dev.waiz.datamanager.model.user;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface userrepository extends JpaRepository<user, UUID> {
    
    // Find user by full name
    Optional<user> findByFullName(String fullName);
    
    // Find user by company name
    Optional<user> findByCompanyName(String companyName);
    
    // Find user by phone number
    Optional<user> findByPhoneNo(String phoneNo);
    
    // Find user by account ID
    Optional<user> findByAccount_AccountId(UUID accountId);
    
    // Check if company name exists
    boolean existsByCompanyName(String companyName);
    
    // Check if phone number exists
    boolean existsByPhoneNo(String phoneNo);
    
    // Find users by partial company name (search)
    List<user> findByCompanyNameContainingIgnoreCase(String companyName);
}
