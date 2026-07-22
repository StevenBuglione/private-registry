package com.stevenbuglione.registry.model;

import java.time.Instant;

public record Approval(String type, String status, String decidedBy, Instant decidedAt) {}
