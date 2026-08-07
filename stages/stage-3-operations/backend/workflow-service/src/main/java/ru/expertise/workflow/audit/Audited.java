package ru.expertise.workflow.audit;

import ru.expertise.workflow.events.DomainEventType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a service method whose successful completion must append one row to the process journal
 * ({@code process_event_log}). The method body populates {@link AuditContext} with the entities and
 * details it touched; {@code AuditAspect} performs the actual write after the method returns.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Audited {
    DomainEventType value();
}
