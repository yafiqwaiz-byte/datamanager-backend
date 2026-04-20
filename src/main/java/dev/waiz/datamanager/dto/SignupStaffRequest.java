package dev.waiz.datamanager.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SignupStaffRequest {
    private String username;
    private String password;
    private String fullName;
    private String department;
    private String position;
}
