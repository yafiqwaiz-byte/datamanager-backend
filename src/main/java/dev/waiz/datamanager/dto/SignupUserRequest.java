package dev.waiz.datamanager.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SignupUserRequest {
    private String username;
    private String password;
    private String fullName;
    private String companyName;
    private String phoneNo;
    private String companyAddress;
}
