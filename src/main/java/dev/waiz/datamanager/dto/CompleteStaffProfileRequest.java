package dev.waiz.datamanager.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CompleteStaffProfileRequest {

    private String department;
    private String position;
}
