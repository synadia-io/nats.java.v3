package io.synadia.client;

/**
 * Interface to allow debug logging of some connection time tracing
 */
public interface TimeTraceLogger {
    /**
     * Trace the object
     * @param format a format string for the args
     * @param args the args
     */
    void trace(String format, Object... args);
}
