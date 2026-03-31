package com.ctps.ctps_api.global.time;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class DateTimeSupport {

    public static final ZoneId ASIA_SEOUL = ZoneId.of("Asia/Seoul");
    public static final ZoneOffset UTC = ZoneOffset.UTC;

    public static LocalDate todayInSeoul(Clock clock) {
        return clock.instant().atZone(ASIA_SEOUL).toLocalDate();
    }

    public static LocalDateTime nowUtc(Clock clock) {
        return LocalDateTime.ofInstant(clock.instant(), UTC);
    }

    public static OffsetDateTime asUtcOffsetDateTime(LocalDateTime value) {
        return value == null ? null : value.atOffset(UTC);
    }

    public static OffsetDateTime asSeoulStartOfDay(LocalDate value) {
        return value == null ? null : value.atStartOfDay(ASIA_SEOUL).toOffsetDateTime();
    }
}
