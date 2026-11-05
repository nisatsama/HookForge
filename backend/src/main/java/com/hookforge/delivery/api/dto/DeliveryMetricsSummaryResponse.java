package com.hookforge.delivery.api.dto;

public record DeliveryMetricsSummaryResponse(
        long total,
        long pending,
        long processing,
        long retrying,
        long succeeded,
        long failed,
        long dead
) {
}