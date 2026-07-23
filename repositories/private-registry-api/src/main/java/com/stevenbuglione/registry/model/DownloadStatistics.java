package com.stevenbuglione.registry.model;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

public record DownloadStatistics(
    long allTime,
    @Nullable Long week,
    @Nullable Long month,
    @Nullable Long year,
    @Nullable Instant lastDownloadedAt,
    Instant observedAt) {}
