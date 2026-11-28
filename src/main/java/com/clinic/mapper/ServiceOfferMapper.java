package com.clinic.mapper;

import com.clinic.dto.response.ServiceOffer;
import com.clinic.entity.DentalService;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

/** Builds the {@link ServiceOffer} view of a treatment's offer, judged against the clinic's clock. */
@Component
public class ServiceOfferMapper {

    private final ZoneId zone;
    private final Clock clock;

    public ServiceOfferMapper(ZoneId clinicZoneId, Clock clinicClock) {
        this.zone = clinicZoneId;
        this.clock = clinicClock;
    }

    /** @param publicView ended offers are hidden from the public (null), but shown to admins */
    public ServiceOffer toOffer(DentalService s, boolean publicView) {
        if (!s.hasOffer()) {
            return null;
        }
        LocalDate today = LocalDate.now(clock);
        ServiceOffer.Status status = today.isBefore(s.getOfferStartDate()) ? ServiceOffer.Status.SCHEDULED
                : today.isAfter(s.getOfferEndDate()) ? ServiceOffer.Status.ENDED
                : ServiceOffer.Status.ACTIVE;
        if (publicView && status == ServiceOffer.Status.ENDED) {
            return null;
        }
        return new ServiceOffer(
                s.getOfferDiscountPercent(),
                s.getOfferStartDate(),
                s.getOfferEndDate(),
                s.getOfferStartDate().atStartOfDay(zone).toInstant(),
                s.getOfferEndDate().plusDays(1).atStartOfDay(zone).toInstant(),
                status);
    }
}
