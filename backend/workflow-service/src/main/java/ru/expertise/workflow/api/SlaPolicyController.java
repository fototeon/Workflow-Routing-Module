package ru.expertise.workflow.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.expertise.workflow.api.dto.ProcessEventLogDtos;
import ru.expertise.workflow.api.dto.SlaPolicyDtos;
import ru.expertise.workflow.domain.SlaPolicy;
import ru.expertise.workflow.sla.SlaPolicyService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/sla-policies")
public class SlaPolicyController {

    private final SlaPolicyService service;

    public SlaPolicyController(SlaPolicyService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SlaPolicyDtos.Response> create(@Valid @RequestBody SlaPolicyDtos.Request request) {
        SlaPolicy policy = service.create(request.code(), request.name(), request.durationMinutes(),
                request.businessHoursOnly(), request.escalationRules());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(policy));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public SlaPolicyDtos.Response update(@PathVariable UUID id, @Valid @RequestBody SlaPolicyDtos.Request request) {
        SlaPolicy policy = service.update(id, request.name(), request.durationMinutes(),
                request.businessHoursOnly(), request.escalationRules());
        return toResponse(policy);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public SlaPolicyDtos.Response get(@PathVariable UUID id) {
        return toResponse(service.get(id));
    }

    /** Configuration journal of the policy (TZ §10). */
    @GetMapping("/{id}/journal")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'ANALYST')")
    public List<ProcessEventLogDtos.Response> journal(@PathVariable UUID id) {
        return service.getJournal(id).stream()
                .map(log -> new ProcessEventLogDtos.Response(log.getId(), null, null, log.getEventType(),
                        log.getPayload(), log.getCorrelationId(), log.getActorId(), log.getOccurredAt()))
                .toList();
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<SlaPolicyDtos.Response> list() {
        return service.list().stream().map(SlaPolicyController::toResponse).toList();
    }

    private static SlaPolicyDtos.Response toResponse(SlaPolicy policy) {
        return new SlaPolicyDtos.Response(policy.getId(), policy.getCode(), policy.getName(),
                policy.getDurationMinutes(), policy.isBusinessHoursOnly(), policy.getEscalationRules());
    }
}
