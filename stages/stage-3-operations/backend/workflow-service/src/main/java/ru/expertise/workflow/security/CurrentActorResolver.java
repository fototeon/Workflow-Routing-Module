package ru.expertise.workflow.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentActorResolver {

    private static final String SYSTEM_ACTOR = "system";

    public String currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || authentication.getName() == null) {
            return SYSTEM_ACTOR;
        }
        return authentication.getName();
    }
}
