package com.careconnect.common;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** The hospital's calendar. Census days, "today", and due dates follow the site's time zone, not UTC. */
@Component
public class SiteClock {

    private final ZoneId zone;

    public SiteClock(@Value("${careconnect.site-zone:Australia/Sydney}") String zone) {
        this.zone = ZoneId.of(zone);
    }

    public ZoneId zone() { return zone; }

    public LocalDate today() { return LocalDate.now(zone); }

    /** Start of the given site day, as a database timestamp. */
    public Timestamp startOf(LocalDate day) { return Timestamp.from(day.atStartOfDay(zone).toInstant()); }

    public Instant at(LocalDate day, int hour, int minute) { return day.atTime(hour, minute).atZone(zone).toInstant(); }
}
