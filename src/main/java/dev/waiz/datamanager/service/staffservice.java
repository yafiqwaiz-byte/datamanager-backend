package dev.waiz.datamanager.service;

import dev.waiz.datamanager.model.staff;
import dev.waiz.datamanager.repository.staffrepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class staffservice {

    
    private final staffrepository staffRepository;

    // CREATE - Save a new staff member
    public staff createStaff(staff newStaff) {
        return staffRepository.save(newStaff);
    }

    // READ - Get all staff members
    public List<staff> getAllStaff() {
        return staffRepository.findAll();
    }

    // READ - Get staff by ID
    public Optional<staff> getStaffById(UUID staffId) {
        return staffRepository.findById(staffId);
    }

    // READ - Get staff by full name
    public Optional<staff> getStaffByFullName(String fullName) {
        return staffRepository.findByFullName(fullName);
    }

    // READ - Get all staff by department
    public List<staff> getStaffByDepartment(String department) {
        return staffRepository.findByDepartment(department);
    }

    // READ - Get all staff by position
    public List<staff> getStaffByPosition(String position) {
        return staffRepository.findByPosition(position);
    }

    // READ - Get staff by account ID
    public Optional<staff> getStaffByAccountId(UUID accountId) {
        return staffRepository.findByAccount_AccountId(accountId);
    }

    // READ - Get staff by department and position
    public List<staff> getStaffByDepartmentAndPosition(String department, String position) {
        return staffRepository.findByDepartmentAndPosition(department, position);
    }

    // UPDATE - Update staff details
    public staff updateStaff(UUID staffId, staff updatedStaff) {
        return staffRepository.findById(staffId)
                .map(existingStaff -> {
                    // Update fields
                    if (updatedStaff.getFullName() != null) {
                        existingStaff.setFullName(updatedStaff.getFullName());
                    }
                    if (updatedStaff.getDepartment() != null) {
                        existingStaff.setDepartment(updatedStaff.getDepartment());
                    }
                    if (updatedStaff.getPosition() != null) {
                        existingStaff.setPosition(updatedStaff.getPosition());
                    }
                    return staffRepository.save(existingStaff);
                })
                .orElse(null);
    }

    // UPDATE - Update staff department
    public staff updateStaffDepartment(UUID staffId, String department) {
        return staffRepository.findById(staffId)
                .map(existingStaff -> {
                    existingStaff.setDepartment(department);
                    return staffRepository.save(existingStaff);
                })
                .orElse(null);
    }

    // UPDATE - Update staff position
    public staff updateStaffPosition(UUID staffId, String position) {
        return staffRepository.findById(staffId)
                .map(existingStaff -> {
                    existingStaff.setPosition(position);
                    return staffRepository.save(existingStaff);
                })
                .orElse(null);
    }

    // DELETE - Delete staff by ID
    public boolean deleteStaff(UUID staffId) {
        if (staffRepository.existsById(staffId)) {
            staffRepository.deleteById(staffId);
            return true;
        }
        return false;
    }

    // CHECK - Verify if staff exists by full name
    public boolean staffExists(String fullName) {
        return staffRepository.existsByFullName(fullName);
    }

    // COUNT - Get total number of staff
    public long getTotalStaffCount() {
        return staffRepository.count();
    }

    // COUNT - Get staff count by department
    public long getStaffCountByDepartment(String department) {
        return staffRepository.findByDepartment(department).size();
    }
}
