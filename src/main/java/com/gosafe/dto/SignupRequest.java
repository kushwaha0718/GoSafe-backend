package com.gosafe.dto;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class SignupRequest {
    @NotBlank(message = "Full name is required.")
    @Size(min = 2, max = 80, message = "Name must be 2–80 characters.")
    @Pattern(regexp = "^[a-zA-Z\\u00C0-\\u024F\\s'\\-]+$", message = "Name can only contain letters, spaces, hyphens and apostrophes.")
    private String name;

    @NotBlank(message = "Email address is required.")
    @Email(message = "Enter a valid email address.")
    private String email;

    @NotBlank(message = "Password is required.")
    @Size(min = 6, max = 128, message = "Password must be 6–128 characters.")
    @Pattern(regexp = ".*[A-Za-z].*", message = "Password must contain at least one letter.")
    @Pattern(regexp = ".*[0-9].*", message = "Password must contain at least one number.")
    private String password;
}
