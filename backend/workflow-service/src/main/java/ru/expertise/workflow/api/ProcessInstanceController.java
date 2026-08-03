package ru.expertise.workflow.api;

import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.expertise.workflow.api.dto.PageResponse;
import ru.expertise.workflow.api.dto.ProcessEventLogDtos;
import ru.expertise.workflow.api.dto.ProcessInstanceDtos;
import ru.expertise.workflow.domain.ProcessEventLog;
import ru.expertise.workflow.domain.ProcessInstance;
import ru.expertise.workflow.domain.ProcessInstanceStatus;
import ru.expertise.workflow.process.ProcessInstanceService;
import ru.expertise.workflow.process.ExportRows;
import ru.expertise.workflow.process.ProcessInstanceSpecifications;
import ru.expertise.workflow.security.CurrentActorResolver;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/process-instances")
public class ProcessInstanceController {

    private final ProcessInstanceService service;
    private final CurrentActorResolver currentActorResolver;

    public ProcessInstanceController(ProcessInstanceService service, CurrentActorResolver currentActorResolver) {
        this.service = service;
        this.currentActorResolver = currentActorResolver;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('COORDINATOR', 'MANAGER', 'ADMIN')")
    public ResponseEntity<ProcessInstanceDtos.Response> start(@Valid @RequestBody ProcessInstanceDtos.StartRequest request) {
        ProcessInstance instance = service.startInstance(request.processDefinitionId(), request.businessKey(),
                request.attributes() == null ? Map.of() : request.attributes(), request.correlationId());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(instance));
    }

    @PostMapping("/{id}/sub-processes")
    @PreAuthorize("hasAnyRole('COORDINATOR', 'MANAGER', 'ADMIN')")
    public ResponseEntity<ProcessInstanceDtos.Response> startSubProcess(@PathVariable UUID id,
                                                                         @Valid @RequestBody ProcessInstanceDtos.SubProcessRequest request) {
        ProcessInstance instance = service.startSubProcess(id, request.processDefinitionId(), request.businessKey(),
                request.attributes() == null ? Map.of() : request.attributes(), request.correlationId());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(instance));
    }

    @PostMapping("/{id}/suspend")
    @PreAuthorize("hasAnyRole('COORDINATOR', 'MANAGER', 'ADMIN')")
    public ProcessInstanceDtos.Response suspend(@PathVariable UUID id, @Valid @RequestBody ProcessInstanceDtos.TransitionRequest request) {
        return toResponse(service.suspend(id, request.reason(), currentActorResolver.currentActor()));
    }

    @PostMapping("/{id}/resume")
    @PreAuthorize("hasAnyRole('COORDINATOR', 'MANAGER', 'ADMIN')")
    public ProcessInstanceDtos.Response resume(@PathVariable UUID id) {
        return toResponse(service.resume(id, currentActorResolver.currentActor()));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('COORDINATOR', 'MANAGER', 'ADMIN')")
    public ProcessInstanceDtos.Response cancel(@PathVariable UUID id, @Valid @RequestBody ProcessInstanceDtos.TransitionRequest request) {
        return toResponse(service.cancel(id, request.reason(), currentActorResolver.currentActor()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ProcessInstanceDtos.Response get(@PathVariable UUID id) {
        return toResponse(service.getForCurrentUser(id));
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public PageResponse<ProcessInstanceDtos.Response> search(
            @RequestParam(required = false) ProcessInstanceStatus status,
            @RequestParam(required = false) String businessKey,
            @RequestParam(required = false) String processDefinitionCode,
            Pageable pageable) {
        var page = service.search(ProcessInstanceSpecifications.filter(status, businessKey, processDefinitionCode), pageable);
        return PageResponse.of(page, ProcessInstanceController::toResponse);
    }

    @GetMapping("/{id}/events")
    @PreAuthorize("isAuthenticated()")
    public List<ProcessEventLogDtos.Response> getEventLog(@PathVariable UUID id) {
        service.getForCurrentUser(id);
        return service.getEventLog(id).stream().map(ProcessInstanceController::toResponse).toList();
    }

    /** CSV export of the current filter, for roles that are allowed to take data out (TZ §9). */
    @GetMapping(value = "/export", produces = "text/csv; charset=UTF-8")
    @PreAuthorize("hasAnyRole('ANALYST', 'MANAGER', 'ADMIN')")
    public ResponseEntity<String> export(@RequestParam(required = false) ProcessInstanceStatus status,
                                          @RequestParam(required = false) String businessKey,
                                          @RequestParam(required = false) String processDefinitionCode) {
        String csv = CsvWriter.render(ExportRows.PROCESS_HEADER,
                service.exportRows(ProcessInstanceSpecifications.filter(status, businessKey, processDefinitionCode)));
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"process-instances.csv\"")
                .body(csv);
    }

    private static ProcessInstanceDtos.Response toResponse(ProcessInstance instance) {
        return new ProcessInstanceDtos.Response(
                instance.getId(), instance.getProcessDefinition().getId(), instance.getProcessVersion(),
                instance.getBusinessKey(), instance.getStatus().name(), instance.getCurrentStepCode(),
                instance.getParentInstance() == null ? null : instance.getParentInstance().getId(),
                instance.getStartedAt(), instance.getCompletedAt(), instance.getCreatedAt(), instance.getUpdatedAt());
    }

    private static ProcessEventLogDtos.Response toResponse(ProcessEventLog log) {
        return new ProcessEventLogDtos.Response(
                log.getId(),
                log.getProcessInstance() == null ? null : log.getProcessInstance().getId(),
                log.getTaskInstance() == null ? null : log.getTaskInstance().getId(),
                log.getEventType(), log.getPayload(), log.getCorrelationId(), log.getActorId(), log.getOccurredAt());
    }
}
