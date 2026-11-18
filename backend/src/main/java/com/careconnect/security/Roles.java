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
    /** Manages CareConnect sign-in accounts. Has no access to patient data. */
    public static final String ADMIN = "ADMIN";

    /** Every role an account can hold. */
    public static final java.util.List<String> ALL = java.util.List.of(CLINICIAN, COORDINATOR, TRIAGE, PRIVACY, MANAGER, SAFETY, ADMIN, INTEGRATION);

    public static final String CARE_STAFF = "hasAnyRole('CLINICIAN','COORDINATOR')";

    private Roles() {}
}
