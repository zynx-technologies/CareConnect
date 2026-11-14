package com.careconnect.integration;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/integration")
public class Hl7Controller {

    private final Hl7Service service;

    public Hl7Controller(Hl7Service service) { this.service = service; }

    @PostMapping(value = "/hl7", consumes = {"text/plain", "x-application/hl7-v2+er7"}, produces = "text/plain")
    @PreAuthorize("hasRole('INTEGRATION')")
    public ResponseEntity<String> receive(@RequestBody String message) {
        Hl7Service.Result result = service.receive(message);
        return result.accepted() ? ResponseEntity.ok(result.ack()) : ResponseEntity.unprocessableContent().body(result.ack());
    }
}
