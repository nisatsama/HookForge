package com.hookforge.delivery.domain;

public enum DeliveryStatus {
    PENDING,
    PROCESSING,
    RETRYING,
    SUCCEEDED,
    FAILED,
    DEAD
}