package com.clinic.entity;

import java.time.LocalTime;

/** Common view of a day's working hours (weekly schedule row or a date-specific override). */
public interface WorkingHours {

    LocalTime getStartTime();

    LocalTime getEndTime();

    LocalTime getBreakStartTime();

    LocalTime getBreakEndTime();

    Integer getSlotDurationMinutes();
}
