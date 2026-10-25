
package com.hookforge.endpoint.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateEndpointRequest(

        @NotBlank(message = "Endpoint name is required")
        @Size(max = 150, message = "Name cannot exceed 150 characters")
        String name,

        @NotBlank(message = "Endpoint URL is required")
        @Size(max = 2048, message = "URL cannot exceed 2048 characters")
        String url

) {
}
