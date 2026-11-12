package com.hookforge.tenant.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "bootstrap_state")
public class BootstrapState {

    @Id
    private Short id;

    @Column(nullable = false)
    private boolean initialized;

    @Column(name = "initialized_at")
    private Instant initializedAt;

    protected BootstrapState() {
    }

    public boolean isInitialized() {
        return initialized;
    }

    public void markInitialized() {
        this.initialized = true;
        this.initializedAt = Instant.now();
    }
}