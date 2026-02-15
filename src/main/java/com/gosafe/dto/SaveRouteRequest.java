package com.gosafe.dto;
import lombok.Data;

@Data
public class SaveRouteRequest {
    private String origin;
    private String destination;
    private String route_name;
    private Object route_data;
    private String label;
}
