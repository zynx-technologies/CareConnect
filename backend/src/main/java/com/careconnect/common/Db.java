package com.careconnect.common;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

public final class Db {

    private Db() {}

    public static String newId() { return UUID.randomUUID().toString(); }

    public static Timestamp now() { return Timestamp.from(Instant.now()); }

    public static Timestamp ts(Instant instant) { return instant == null ? null : Timestamp.from(instant); }
}
