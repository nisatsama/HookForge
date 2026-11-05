package com.hookforge.delivery.api.dto;

import java.util.List;

public record DeliveryPageResponse(
        List<DeliveryResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean last
) {
}