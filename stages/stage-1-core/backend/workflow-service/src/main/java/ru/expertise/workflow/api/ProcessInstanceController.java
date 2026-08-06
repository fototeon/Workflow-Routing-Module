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
import ru.expertise.workflow.api.dto.ProcessInstanceDtos;
import ru.expertise.workflow.domain.ProcessInstance;
import ru.expertise.workflow.domain.ProcessInstanceStatus;
import ru.expertise.workflow.process.ProcessInstanceService;
import ru.expertise.workflow.process.ProcessInstanceSpecifications;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/process-instances")
public class ProcessInstanceController {

    private final ProcessInstanceService service;

    public ProcessInstanceController(ProcessInstanceService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('COORDINATOR', 'MANAGER', 'ADMIN')")
    public ResponseEntity<ProcessInstanceDtos.Response> start(@Valid @RequestBody ProcessInstanceDtos.StartRequest request) {
        ProcessInstance instance = service.startInstance(request.processDefinitionId(), request.businessKey(),
                request.attributes() == null ? Map.of() : request.attributes());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(instance));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('COORDINATOR', 'MANAGER', 'ADMIN')")
    public ProcessInstanceDtos.Response cancel(@PathVariable UUID id) {
        return toResponse(service.cancel(id));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ProcessInstanceDtos.Response get(@PathVariable UUID id) {
        return toResponse(service.get(id));
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

    private static ProcessInstanceDtos.Response toResponse(ProcessInstance instance) {
        return new ProcessInstanceDtos.Response(
                instance.getId(), instance.getProcessDefinition().getId(), instance.getProcessVersion(),
                instance.getBusinessKey(), instance.getStatus().name(), instance.getCurrentStepCode(),
                instance.getStartedAt(), instance.getCompletedAt(), instance.getCreatedAt(), instance.getUpdatedAt());
    }
}
