package com.careconnect.security;

/** Entra ID app roles. Authorities are prefixed with ROLE_ by SecurityConfig. */
public final class Roles {

    public static final String CLINICIAN = "CLINICIAN";
    public static final String COORDINATOR = "COORDINATOR";
    public static final String TRIAGE = "TRIAGE";
    public static final String PRIVACY = "PRIVACY";
    public static final String INTEGRATION = "INTEGRATION";
    /** Bed and nursing managers. */
    public static final String MANAGER = "MANAGER";
    /** Clinical safety officer and safety committee members. */
    public static final String SAFETY = "SAFETY";

    public static final String CARE_STAFF = "hasAnyRole('CLINICIAN','COORDINATOR')";

    private Roles() {}
}
