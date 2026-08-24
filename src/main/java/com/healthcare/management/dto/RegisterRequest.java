package com.healthcare.management.dto;

import com.healthcare.management.model.Role;
import lombok.*;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RegisterRequest {
    private String fullName;
    private String email;
    private String password;
    private Role role;
}
