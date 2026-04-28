package dev.waiz.datamanager.repository;

import dev.waiz.datamanager.model.staff;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface staffrepository extends JpaRepository<staff, UUID> {
    
    // Find staff by full name
    Optional<staff> findByFullName(String fullName);
    
    // Find all staff by department
    List<staff> findByDepartment(String department);
    
    // Find all staff by position
    List<staff> findByPosition(String position);
    
    // Find staff by account ID
    Optional<staff> findByAccount_AccountId(UUID accountId);
    
    // Find staff by username
    Optional<staff> findByAccount_Username(String username);    
    
    // Check if staff exists by full name
    boolean existsByFullName(String fullName);
    
    // Find staff by department and position
    List<staff> findByDepartmentAndPosition(String department, String position);
}
