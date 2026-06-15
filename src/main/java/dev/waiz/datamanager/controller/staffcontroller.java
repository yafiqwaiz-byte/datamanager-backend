package dev.waiz.datamanager.controller;

import dev.waiz.datamanager.model.staff;
import dev.waiz.datamanager.service.staffservice;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/staff")
public class staffcontroller {

    
    private final staffservice staffService;

    // CREATE - Create a new staff member
    @PostMapping
    public ResponseEntity<?> createStaff(@RequestBody staff newStaff) {
        // Check if full name already exists
        if (staffService.staffExists(newStaff.getFullName())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Staff member with this name already exists");
        }
        staff createdStaff = staffService.createStaff(newStaff);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdStaff);
    }

    // READ - Get all staff members
    @GetMapping
    public ResponseEntity<List<staff>> getAllStaff() {
        List<staff> staffList = staffService.getAllStaff();
        return ResponseEntity.ok(staffList);
    }

    // READ - Get staff by ID
    @GetMapping("/{staffId}")
    public ResponseEntity<?> getStaffById(@PathVariable UUID staffId) {
        Optional<staff> foundStaff = staffService.getStaffById(staffId);
        if (foundStaff.isPresent()) {
            return ResponseEntity.ok(foundStaff.get());
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Staff member not found");
    }

    // READ - Get staff by full name
    @GetMapping("/search/name")
    public ResponseEntity<?> getStaffByName(@RequestParam String fullName) {
        Optional<staff> foundStaff = staffService.getStaffByFullName(fullName);
        if (foundStaff.isPresent()) {
            return ResponseEntity.ok(foundStaff.get());
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Staff member not found");
    }

    // READ - Get all staff by department
    @GetMapping("/department/{department}")
    public ResponseEntity<?> getStaffByDepartment(@PathVariable String department) {
        List<staff> staffList = staffService.getStaffByDepartment(department);
        if (staffList.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("No staff found in " + department + " department");
        }
        return ResponseEntity.ok(staffList);
    }

    // READ - Get all staff by position
    @GetMapping("/position/{position}")
    public ResponseEntity<?> getStaffByPosition(@PathVariable String position) {
        List<staff> staffList = staffService.getStaffByPosition(position);
        if (staffList.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("No staff found with position: " + position);
        }
        return ResponseEntity.ok(staffList);
    }

    // READ - Get staff by department and position
    @GetMapping("/search")
    public ResponseEntity<?> getStaffByDepartmentAndPosition(
            @RequestParam String department,
            @RequestParam String position) {
        List<staff> staffList = staffService.getStaffByDepartmentAndPosition(department, position);
        if (staffList.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("No staff found in " + department + " with position: " + position);
        }
        return ResponseEntity.ok(staffList);
    }

    // READ - Get staff by account ID
    @GetMapping("/account/{accountId}")
    public ResponseEntity<?> getStaffByAccountId(@PathVariable UUID accountId) {
        Optional<staff> foundStaff = staffService.getStaffByAccountId(accountId);
        if (foundStaff.isPresent()) {
            return ResponseEntity.ok(foundStaff.get());
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Staff member with this account not found");
    }

    // UPDATE - Update staff details
    @PutMapping("/{staffId}")
    public ResponseEntity<?> updateStaff(@PathVariable UUID staffId, @RequestBody staff updatedStaff) {
        staff updated = staffService.updateStaff(staffId, updatedStaff);
        if (updated != null) {
            return ResponseEntity.ok(updated);
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Staff member not found");
    }

    // UPDATE - Update staff department
    @PatchMapping("/{staffId}/department")
    public ResponseEntity<?> updateDepartment(@PathVariable UUID staffId, @RequestParam String department) {
        staff updated = staffService.updateStaffDepartment(staffId, department);
        if (updated != null) {
            return ResponseEntity.ok(updated);
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Staff member not found");
    }

    // UPDATE - Update staff position
    @PatchMapping("/{staffId}/position")
    public ResponseEntity<?> updatePosition(@PathVariable UUID staffId, @RequestParam String position) {
        staff updated = staffService.updateStaffPosition(staffId, position);
        if (updated != null) {
            return ResponseEntity.ok(updated);
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Staff member not found");
    }

    // DELETE - Delete staff member
    @DeleteMapping("/{staffId}")
    public ResponseEntity<?> deleteStaff(@PathVariable UUID staffId) {
        boolean deleted = staffService.deleteStaff(staffId);
        if (deleted) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Staff member not found");
    }

    // GET - Total staff count
    @GetMapping("/stats/total")
    public ResponseEntity<?> getTotalStaffCount() {
        long totalCount = staffService.getTotalStaffCount();
        return ResponseEntity.ok(new StaffCountResponse(totalCount));
    }

    // GET - Staff count by department
    @GetMapping("/stats/department/{department}")
    public ResponseEntity<?> getStaffCountByDepartment(@PathVariable String department) {
        long count = staffService.getStaffCountByDepartment(department);
        return ResponseEntity.ok(new DepartmentStaffCountResponse(department, count));
    }

    // Helper class for staff count response
    public static class StaffCountResponse {
        public long totalStaff;

        public StaffCountResponse(long totalStaff) {
            this.totalStaff = totalStaff;
        }
    }

    // Helper class for department staff count response
    public static class DepartmentStaffCountResponse {
        public String department;
        public long staffCount;

        public DepartmentStaffCountResponse(String department, long staffCount) {
            this.department = department;
            this.staffCount = staffCount;
        }
    }
}



