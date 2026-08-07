package ru.expertise.workflow.api;

import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.expertise.workflow.api.dto.PageResponse;
import ru.expertise.workflow.api.dto.TaskInstanceDtos;
import ru.expertise.workflow.domain.TaskInstance;
import ru.expertise.workflow.domain.TaskInstanceStatus;
import ru.expertise.workflow.process.ProcessInstanceService;
import ru.expertise.workflow.process.TaskInstanceService;
import ru.expertise.workflow.process.TaskInstanceSpecifications;
import ru.expertise.workflow.security.CurrentActorResolver;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/tasks")
public class TaskInstanceController {

    private final TaskInstanceService taskInstanceService;
    private final ProcessInstanceService processInstanceService;
    private final CurrentActorResolver currentActorResolver;

    public TaskInstanceController(TaskInstanceService taskInstanceService,
                                   ProcessInstanceService processInstanceService,
                                   CurrentActorResolver currentActorResolver) {
        this.taskInstanceService = taskInstanceService;
        this.processInstanceService = processInstanceService;
        this.currentActorResolver = currentActorResolver;
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public TaskInstanceDtos.Response get(@PathVariable UUID id) {
        return toResponse(taskInstanceService.get(id));
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public PageResponse<TaskInstanceDtos.Response> search(
            @RequestParam(required = false) TaskInstanceStatus status,
            @RequestParam(required = false) String assigneeId,
            @RequestParam(required = false) String assigneeRole,
            @RequestParam(required = false) UUID processInstanceId,
            Pageable pageable) {
        var page = taskInstanceService.search(
                TaskInstanceSpecifications.filter(status, assigneeId, assigneeRole, processInstanceId), pageable);
        return PageResponse.of(page, TaskInstanceController::toResponse);
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAnyRole('COORDINATOR', 'MANAGER', 'ADMIN')")
    public TaskInstanceDtos.Response complete(@PathVariable UUID id, @RequestBody(required = false) TaskInstanceDtos.CompleteRequest request) {
        Map<String, Object> outcome = request == null || request.outcomeAttributes() == null ? Map.of() : request.outcomeAttributes();
        String correlationId = request == null ? null : request.correlationId();
        processInstanceService.completeTaskAndAdvance(id, currentActorResolver.currentActor(), outcome, correlationId);
        return toResponse(taskInstanceService.get(id));
    }

    private static TaskInstanceDtos.Response toResponse(TaskInstance task) {
        return new TaskInstanceDtos.Response(
                task.getId(), task.getProcessInstance().getId(), task.getStepCode(), task.getName(),
                task.getAssigneeId(), task.getAssigneeRole(), task.getStatus().name(),
                task.getDueAt(), task.getCompletedAt(), task.getCreatedAt(), task.getUpdatedAt());
    }
}
