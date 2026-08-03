package ru.expertise.workflow.security;

import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import ru.expertise.workflow.config.WorkflowProperties;
import ru.expertise.workflow.domain.ProcessInstance;
import ru.expertise.workflow.domain.ProcessInstanceStatus;
import ru.expertise.workflow.domain.TaskInstance;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Attribute-based access model on top of the role model (TZ §10: "доступ ... с учетом организации,
 * статуса процесса и принадлежности к делу").
 *
 * <p>Roles listed in {@code workflow.access.full-access-roles} — management and the reporting role —
 * see everything, because supervising and building indicators require the whole picture (TZ §3).
 * Everyone else sees a case only when they are connected to it:
 * <ul>
 *   <li>they started it, or it is registered to them personally;</li>
 *   <li>it belongs to their organization and is still in flight;</li>
 *   <li>a task in it is assigned to them or to one of their roles.</li>
 * </ul>
 * The organization comes from a JWT claim, because the organization directory itself belongs to
 * another service (TZ §2) — this module only matches the value it is told.
 */
@Component
public class AccessPolicy {

    private static final List<ProcessInstanceStatus> TERMINAL_STATUSES =
            List.of(ProcessInstanceStatus.COMPLETED, ProcessInstanceStatus.CANCELLED);

    private final WorkflowProperties properties;
    private final CurrentActorResolver currentActorResolver;

    public AccessPolicy(WorkflowProperties properties, CurrentActorResolver currentActorResolver) {
        this.properties = properties;
        this.currentActorResolver = currentActorResolver;
    }

    public boolean hasFullAccess() {
        Set<String> fullAccess = Set.copyOf(properties.getAccess().getFullAccessRoles());
        return currentRoles().stream().anyMatch(fullAccess::contains);
    }

    /** Roles of the current user, without the Spring Security {@code ROLE_} prefix. */
    public Set<String> currentRoles() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return Set.of();
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(authority -> authority.startsWith("ROLE_") ? authority.substring(5) : authority)
                .collect(Collectors.toUnmodifiableSet());
    }

    public String currentOrganization() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtToken)) {
            return null;
        }
        Jwt jwt = jwtToken.getToken();
        Object claim = jwt.getClaim(properties.getAccess().getOrganizationClaim());
        return claim == null ? null : String.valueOf(claim);
    }

    /** Restricts a process-instance query to what the current user may see. */
    public Specification<ProcessInstance> visibleInstances() {
        if (hasFullAccess()) {
            return (root, query, cb) -> cb.conjunction();
        }
        String actor = currentActorResolver.currentActor();
        String organization = currentOrganization();
        Set<String> roles = currentRoles();

        return (root, query, cb) -> {
            var involved = cb.or(
                    cb.equal(root.get("createdBy"), actor),
                    cb.equal(root.get("ownerUserId"), actor),
                    cb.exists(assignedTaskSubquery(root, query, cb, actor, roles)));

            if (organization == null || organization.isBlank()) {
                return involved;
            }
            // Organization-wide visibility only covers cases that are still in flight; finished and
            // cancelled ones stay visible to the people who actually worked on them.
            var sameOrgAndActive = cb.and(
                    cb.equal(root.get("ownerOrgId"), organization),
                    cb.not(root.get("status").in(TERMINAL_STATUSES)));
            return cb.or(involved, sameOrgAndActive);
        };
    }

    private Subquery<Integer> assignedTaskSubquery(jakarta.persistence.criteria.Root<ProcessInstance> root,
                                                    jakarta.persistence.criteria.CriteriaQuery<?> query,
                                                    jakarta.persistence.criteria.CriteriaBuilder cb,
                                                    String actor, Set<String> roles) {
        Subquery<Integer> subquery = query.subquery(Integer.class);
        var task = subquery.from(TaskInstance.class);
        var assignedToMe = roles.isEmpty()
                ? cb.equal(task.get("assigneeId"), actor)
                : cb.or(cb.equal(task.get("assigneeId"), actor), task.get("assigneeRole").in(roles));
        return subquery.select(cb.literal(1))
                .where(cb.and(cb.equal(task.get("processInstance"), root), assignedToMe));
    }

    /** Restricts a task query to the tasks the current user is expected to work on. */
    public Specification<TaskInstance> visibleTasks() {
        if (hasFullAccess()) {
            return (root, query, cb) -> cb.conjunction();
        }
        String actor = currentActorResolver.currentActor();
        Set<String> roles = currentRoles();

        return (root, query, cb) -> roles.isEmpty()
                ? cb.equal(root.get("assigneeId"), actor)
                : cb.or(cb.equal(root.get("assigneeId"), actor), root.get("assigneeRole").in(roles));
    }

    /** True when the given instance passes the same rules as {@link #visibleInstances()}. */
    public boolean canSee(ProcessInstance instance, List<TaskInstance> instanceTasks) {
        if (hasFullAccess()) {
            return true;
        }
        String actor = currentActorResolver.currentActor();
        String organization = currentOrganization();
        Set<String> roles = currentRoles();

        boolean involved = actor.equals(instance.getCreatedBy())
                || actor.equals(instance.getOwnerUserId())
                || instanceTasks.stream().anyMatch(task ->
                        actor.equals(task.getAssigneeId()) || roles.contains(task.getAssigneeRole()));
        if (involved) {
            return true;
        }
        return organization != null && !organization.isBlank()
                && organization.equals(instance.getOwnerOrgId())
                && !TERMINAL_STATUSES.contains(instance.getStatus());
    }
}
