package ru.expertise.workflow.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.expertise.workflow.api.dto.ProcessDefinitionDtos;
import ru.expertise.workflow.api.dto.ProcessEventLogDtos;
import ru.expertise.workflow.api.dto.RoutingRuleDtos;
import ru.expertise.workflow.domain.ProcessDefinition;
import ru.expertise.workflow.domain.ProcessDefinitionStatus;
import ru.expertise.workflow.domain.ProcessEventLog;
import ru.expertise.workflow.domain.RoutingRule;
import ru.expertise.workflow.process.ProcessDefinitionService;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@RestController
@RequestMapping("/api/process-definitions")
public class ProcessDefinitionController {

    private final ProcessDefinitionService service;

    public ProcessDefinitionController(ProcessDefinitionService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProcessDefinitionDtos.Response> create(@Valid @RequestBody ProcessDefinitionDtos.Request request) {
        ProcessDefinition definition = service.create(request.code(), request.name(), request.slaPolicyId());
        return ResponseEntity.status(HttpStatus.CREATED).body(withRuleCount(definition));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ProcessDefinitionDtos.Response update(@PathVariable UUID id, @Valid @RequestBody ProcessDefinitionDtos.Request request) {
        return withRuleCount(service.update(id, request.name(), request.slaPolicyId()));
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasRole('ADMIN')")
    public ProcessDefinitionDtos.Response publish(@PathVariable UUID id) {
        return withRuleCount(service.publish(id));
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("hasRole('ADMIN')")
    public ProcessDefinitionDtos.Response archive(@PathVariable UUID id) {
        return withRuleCount(service.archive(id));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ProcessDefinitionDtos.Response get(@PathVariable UUID id) {
        return toResponse(service.getSummary(id));
    }

    /**
     * The template catalogue. Without parameters it answers with published templates only, because
     * that is what the "start a process" picker needs; {@code status=ALL} returns the whole registry
     * (drafts and archives included) for the administration screen, and {@code code} lists every
     * version of one template.
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<ProcessDefinitionDtos.Response> list(@RequestParam(required = false) String code,
                                                     @RequestParam(required = false) String status) {
        return service.list(code, parseStatusFilter(code, status)).stream()
                .map(ProcessDefinitionController::toResponse)
                .toList();
    }

    /** {@code null} means "no status filter"; an absent parameter keeps the published-only default. */
    private static ProcessDefinitionStatus parseStatusFilter(String code, String status) {
        if (status == null || status.isBlank()) {
            return code == null || code.isBlank() ? ProcessDefinitionStatus.PUBLISHED : null;
        }
        if ("ALL".equalsIgnoreCase(status)) {
            return null;
        }
        try {
            return ProcessDefinitionStatus.valueOf(status.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown process definition status: " + status);
        }
    }

    @PostMapping("/{id}/routing-rules")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RoutingRuleDtos.Response> addRoutingRule(@PathVariable UUID id,
                                                                    @Valid @RequestBody RoutingRuleDtos.Request request) {
        RoutingRule rule = service.addRoutingRule(id, request.name(), request.priority(),
                request.conditionTree(), request.targetStepCode(), request.targetRole());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(rule));
    }

    /** Configuration journal of the template (TZ §10, REQ-02-002 "фиксируется в журнале действий"). */
    @GetMapping("/{id}/journal")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'ANALYST')")
    public List<ProcessEventLogDtos.Response> journal(@PathVariable UUID id) {
        return service.getJournal(id).stream().map(ProcessDefinitionController::toResponse).toList();
    }

    @GetMapping("/{id}/routing-rules")
    @PreAuthorize("isAuthenticated()")
    public List<RoutingRuleDtos.Response> getRoutingRules(@PathVariable UUID id) {
        return service.getRoutingRules(id).stream().map(ProcessDefinitionController::toResponse).toList();
    }

    @DeleteMapping("/{id}/routing-rules/{ruleId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteRoutingRule(@PathVariable UUID id, @PathVariable UUID ruleId) {
        service.deleteRoutingRule(id, ruleId);
        return ResponseEntity.noContent().build();
    }

    /** Mutations answer with the same shape as the catalogue, so the UI can refresh a row from the response. */
    private ProcessDefinitionDtos.Response withRuleCount(ProcessDefinition definition) {
        return toResponse(service.getSummary(definition.getId()));
    }

    private static ProcessDefinitionDtos.Response toResponse(ProcessDefinitionService.DefinitionSummary summary) {
        ProcessDefinition definition = summary.definition();
        return new ProcessDefinitionDtos.Response(
                definition.getId(), definition.getCode(), definition.getName(), definition.getVersion(),
                definition.getStatus().name(),
                definition.getSlaPolicy() == null ? null : definition.getSlaPolicy().getId(),
                summary.routingRuleCount(),
                definition.getCreatedAt(), definition.getUpdatedAt());
    }

    private static ProcessEventLogDtos.Response toResponse(ProcessEventLog log) {
        return new ProcessEventLogDtos.Response(
                log.getId(),
                log.getProcessInstance() == null ? null : log.getProcessInstance().getId(),
                log.getTaskInstance() == null ? null : log.getTaskInstance().getId(),
                log.getEventType(), log.getPayload(), log.getCorrelationId(), log.getActorId(), log.getOccurredAt());
    }

    private static RoutingRuleDtos.Response toResponse(RoutingRule rule) {
        return new RoutingRuleDtos.Response(rule.getId(), rule.getProcessDefinition().getId(), rule.getName(),
                rule.getPriority(), rule.getConditionTree(), rule.getTargetStepCode(), rule.getTargetRole());
    }
}
