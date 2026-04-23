package dev.waiz.datamanager.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CompleteUserProfileRequest {

    private String companyName;
    private String phoneNo;
    private String companyAddress;
}
