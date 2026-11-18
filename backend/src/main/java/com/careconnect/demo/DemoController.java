package com.careconnect.demo;

import com.careconnect.audit.AuditService;
import com.careconnect.security.CurrentUser;
import com.careconnect.security.Roles;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Exists only when demo data is on. The portal uses it to label synthetic data and offer the demo care team. */
@RestController
@RequestMapping("/api/v1/demo")
@ConditionalOnProperty(name = "careconnect.demo-data.enabled", havingValue = "true")
public class DemoController {

    private final DemoDataLoader loader;
    private final CurrentUser user;
    private final AuditService audit;

    public DemoController(DemoDataLoader loader, CurrentUser user, AuditService audit) {
        this.loader = loader;
        this.user = user;
        this.audit = audit;
    }

    @GetMapping
    public Map<String, Object> status() {
        return Map.of("syntheticData", true, "onDemoCareTeam", loader.isMember(user.id()));
    }

    /** Care-team membership grants record access, so joining is audited like any other access change. */
    @PostMapping("/care-team")
    @PreAuthorize(Roles.CARE_STAFF)
    public Map<String, Integer> join() {
        int added = loader.join(user.id());
        audit.record(user.id(), "DEMO_CARE_TEAM_JOINED", null, null, "ALLOWED", added + " synthetic patients");
        return Map.of("patientsAdded", added);
    }
}
