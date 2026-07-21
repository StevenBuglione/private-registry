package com.stevenbuglione.registry.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PackageKindTest {

    @Test
    void parsesApiValues() {
        assertThat(PackageKind.from("module")).isEqualTo(PackageKind.MODULE);
        assertThat(PackageKind.from("PROVIDER")).isEqualTo(PackageKind.PROVIDER);
        assertThat(PackageKind.from(null)).isNull();
    }

    @Test
    void rejectsUnknownValues() {
        assertThatThrownBy(() -> PackageKind.from("unknown"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
