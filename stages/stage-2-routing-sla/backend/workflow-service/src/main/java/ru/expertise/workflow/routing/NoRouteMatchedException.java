package ru.expertise.workflow.routing;

import java.util.UUID;

public class NoRouteMatchedException extends RuntimeException {

    public NoRouteMatchedException(UUID processDefinitionId) {
        super("No routing rule matched for process definition " + processDefinitionId);
    }
}
