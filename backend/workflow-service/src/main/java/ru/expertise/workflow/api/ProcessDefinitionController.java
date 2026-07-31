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
import ru.expertise.workflow.api.dto.RoutingRuleDtos;
import ru.expertise.workflow.domain.ProcessDefinition;
import ru.expertise.workflow.domain.RoutingRule;
import ru.expertise.workflow.process.ProcessDefinitionService;

import java.util.List;
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
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(definition));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ProcessDefinitionDtos.Response update(@PathVariable UUID id, @Valid @RequestBody ProcessDefinitionDtos.Request request) {
        return toResponse(service.update(id, request.name(), request.slaPolicyId()));
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasRole('ADMIN')")
    public ProcessDefinitionDtos.Response publish(@PathVariable UUID id) {
        return toResponse(service.publish(id));
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("hasRole('ADMIN')")
    public ProcessDefinitionDtos.Response archive(@PathVariable UUID id) {
        return toResponse(service.archive(id));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ProcessDefinitionDtos.Response get(@PathVariable UUID id) {
        return toResponse(service.get(id));
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<ProcessDefinitionDtos.Response> list(@RequestParam(required = false) String code) {
        List<ProcessDefinition> definitions = code == null || code.isBlank()
                ? service.listPublished()
                : service.listVersions(code);
        return definitions.stream().map(ProcessDefinitionController::toResponse).toList();
    }

    @PostMapping("/{id}/routing-rules")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RoutingRuleDtos.Response> addRoutingRule(@PathVariable UUID id,
                                                                    @Valid @RequestBody RoutingRuleDtos.Request request) {
        RoutingRule rule = service.addRoutingRule(id, request.name(), request.priority(),
                request.conditionTree(), request.targetStepCode(), request.targetRole());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(rule));
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

    private static ProcessDefinitionDtos.Response toResponse(ProcessDefinition definition) {
        return new ProcessDefinitionDtos.Response(
                definition.getId(), definition.getCode(), definition.getName(), definition.getVersion(),
                definition.getStatus().name(),
                definition.getSlaPolicy() == null ? null : definition.getSlaPolicy().getId(),
                definition.getCreatedAt(), definition.getUpdatedAt());
    }

    private static RoutingRuleDtos.Response toResponse(RoutingRule rule) {
        return new RoutingRuleDtos.Response(rule.getId(), rule.getProcessDefinition().getId(), rule.getName(),
                rule.getPriority(), rule.getConditionTree(), rule.getTargetStepCode(), rule.getTargetRole());
    }
}
