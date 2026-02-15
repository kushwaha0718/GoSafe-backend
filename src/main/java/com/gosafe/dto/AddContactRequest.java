package com.gosafe.dto;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class AddContactRequest {
    @NotBlank(message = "Name is required.") @Size(max = 120) private String name;
    @NotBlank(message = "Phone is required.") @Size(max = 25) private String phone;
    @Size(max = 60) private String relation;
}
