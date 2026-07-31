package ru.expertise.workflow.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import ru.expertise.workflow.domain.ProcessEventLog;
import ru.expertise.workflow.domain.ProcessInstance;
import ru.expertise.workflow.domain.TaskInstance;
import ru.expertise.workflow.repository.ProcessEventLogRepository;
import ru.expertise.workflow.security.CurrentActorResolver;

import java.lang.reflect.Method;

/**
 * Around advice for {@link Audited}: lets the target method run, then — only if it completes
 * without throwing — writes one {@link ProcessEventLog} row from whatever the method staged in
 * {@link AuditContext}. Runs inside the caller's transaction, so the audit row commits or rolls
 * back atomically with the domain change it describes.
 */
@Aspect
@Component
public class AuditAspect {

    private final ProcessEventLogRepository processEventLogRepository;
    private final EntityManager entityManager;
    private final CurrentActorResolver currentActorResolver;
    private final ObjectMapper objectMapper;

    public AuditAspect(ProcessEventLogRepository processEventLogRepository,
                        EntityManager entityManager,
                        CurrentActorResolver currentActorResolver,
                        ObjectMapper objectMapper) {
        this.processEventLogRepository = processEventLogRepository;
        this.entityManager = entityManager;
        this.currentActorResolver = currentActorResolver;
        this.objectMapper = objectMapper;
    }

    @Around("@annotation(audited)")
    public Object audit(ProceedingJoinPoint joinPoint, Audited audited) throws Throwable {
        AuditContext.clear();
        Object result = joinPoint.proceed();
        recordEvent(audited, joinPoint);
        return result;
    }

    private void recordEvent(Audited audited, ProceedingJoinPoint joinPoint) {
        AuditContext.Entry ctx = AuditContext.snapshotAndClear();

        ProcessEventLog log = new ProcessEventLog();
        log.setEventType(audited.value().wireName());
        log.setCorrelationId(ctx.getCorrelationId());
        log.setActorId(currentActorResolver.currentActor());
        log.setPayload(objectMapper.valueToTree(ctx.getDetails()));

        if (ctx.getProcessInstanceId() != null) {
            log.setProcessInstance(entityManager.getReference(ProcessInstance.class, ctx.getProcessInstanceId()));
        }
        if (ctx.getTaskInstanceId() != null) {
            log.setTaskInstance(entityManager.getReference(TaskInstance.class, ctx.getTaskInstanceId()));
        }

        if (log.getProcessInstance() == null && log.getTaskInstance() == null) {
            Method method = ((org.aspectj.lang.reflect.MethodSignature) joinPoint.getSignature()).getMethod();
            throw new IllegalStateException(
                    "@Audited method " + method + " completed without setting processInstanceId or taskInstanceId on AuditContext");
        }

        processEventLogRepository.save(log);
    }
}
