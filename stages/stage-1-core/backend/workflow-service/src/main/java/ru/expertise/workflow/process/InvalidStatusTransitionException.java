package ru.expertise.workflow.process;

public class InvalidStatusTransitionException extends RuntimeException {

    public InvalidStatusTransitionException(String from, String to) {
        super("Transition from " + from + " to " + to + " is not allowed");
    }
}
