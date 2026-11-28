package com.clinic.dto.response;

import java.time.Instant;
import java.time.LocalDate;

/**
 * A treatment's time-limited offer. Dates are clinic-local and inclusive; {@code startsAt}/{@code endsAt} are the exact
 * instants (start of the first day, end of the last day) so the website can show a live countdown.
 */
public record ServiceOffer(
        int percent,
        LocalDate startDate,
        LocalDate endDate,
        Instant startsAt,
        Instant endsAt,
        Status status
) {
    public enum Status { SCHEDULED, ACTIVE, ENDED }
}
