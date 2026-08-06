package ru.expertise.workflow.process;

import java.util.UUID;

public class ProcessDefinitionNotPublishedException extends RuntimeException {

    public ProcessDefinitionNotPublishedException(UUID processDefinitionId) {
        super("Process definition " + processDefinitionId + " is not PUBLISHED");
    }
}
