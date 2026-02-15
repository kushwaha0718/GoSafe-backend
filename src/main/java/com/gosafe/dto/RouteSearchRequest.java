package com.gosafe.dto;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class RouteSearchRequest {
    @NotBlank(message = "Origin required.") private String origin;
    @NotBlank(message = "Destination required.") private String destination;
}
