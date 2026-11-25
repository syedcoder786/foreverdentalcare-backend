package com.clinic.enums;

import org.junit.jupiter.api.Test;

import static com.clinic.enums.AppointmentStatus.*;
import static org.assertj.core.api.Assertions.assertThat;

class AppointmentStatusTest {

    @Test
    void pendingCanBeConfirmedOrCancelledOnly() {
        assertThat(PENDING.canTransitionTo(CONFIRMED)).isTrue();
        assertThat(PENDING.canTransitionTo(CANCELLED)).isTrue();
        assertThat(PENDING.canTransitionTo(COMPLETED)).isFalse();
    }

    @Test
    void confirmedCanBeCompletedOrCancelled() {
        assertThat(CONFIRMED.canTransitionTo(COMPLETED)).isTrue();
        assertThat(CONFIRMED.canTransitionTo(CANCELLED)).isTrue();
        assertThat(CONFIRMED.canTransitionTo(PENDING)).isFalse();
    }

    @Test
    void terminalStatusesCannotTransition() {
        assertThat(COMPLETED.allowedTransitions()).isEmpty();
        assertThat(CANCELLED.allowedTransitions()).isEmpty();
    }

    @Test
    void onlyCancelledReleasesTheSlot() {
        assertThat(CANCELLED.occupiesSlot()).isFalse();
        assertThat(PENDING.occupiesSlot()).isTrue();
        assertThat(CONFIRMED.occupiesSlot()).isTrue();
        assertThat(COMPLETED.occupiesSlot()).isTrue();
    }
}
