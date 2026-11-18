package com.careconnect.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** Uses the release-gate limits from the design: AUROC at least 0.72 and calibration error at most 0.05. */
class SubgroupStatusTest {

    private static String status(String auroc, String calibration) {
        return GovernanceController.subgroupStatus(new BigDecimal(auroc), new BigDecimal(calibration),
                new BigDecimal("0.72"), new BigDecimal("0.05"));
    }

    @Test
    void appliesTheReleaseGateLimits() {
        assertThat(status("0.76", "0.03")).isEqualTo("OK");
        assertThat(status("0.74", "0.04")).isEqualTo("OK");
        assertThat(status("0.73", "0.04")).isEqualTo("WATCH");
        assertThat(status("0.75", "0.045")).isEqualTo("WATCH");
        assertThat(status("0.71", "0.06")).isEqualTo("REVIEW");
        assertThat(status("0.80", "0.06")).isEqualTo("REVIEW");
    }
}
