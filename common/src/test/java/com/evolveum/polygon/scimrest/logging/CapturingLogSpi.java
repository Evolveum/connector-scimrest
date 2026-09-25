/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.logging;

import org.identityconnectors.common.logging.Log;
import org.identityconnectors.common.logging.LogSpi;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * In-memory {@link LogSpi} for tests: every logged message is appended to a static queue
 * together with the name of the logger it was emitted through, so tests can observe both the
 * lines a component emitted and which logger emitted them. Registered via
 * {@code META-INF/services/org.identityconnectors.common.logging}.
 *
 * <p>Every level is enabled and nothing is filtered, so tests observe all calls; callers must
 * {@link #clear()} the captured lines before exercising a component.
 */
public final class CapturingLogSpi implements LogSpi {

    /**
     * A captured log line.
     *
     * @param logger  the name of the logger the line was emitted through
     * @param level   the ConnId log level the line was emitted at
     * @param message the message
     */
    public record CapturedLine(String logger, Log.Level level, String message) {
    }

    private static final ConcurrentLinkedQueue<CapturedLine> LINES = new ConcurrentLinkedQueue<>();

    /**
     * Returns the log lines captured so far, in emission order.
     *
     * @return the captured lines
     */
    public static List<CapturedLine> lines() {
        return List.copyOf(LINES);
    }

    /**
     * Returns the messages of the captured lines so far, in emission order.
     *
     * @return the captured messages
     */
    public static List<String> messages() {
        return lines().stream().map(CapturedLine::message).toList();
    }

    /** Removes all captured lines. */
    public static void clear() {
        LINES.clear();
    }

    @Override
    public void log(Class<?> clazz, String method, Log.Level level, String message, Throwable ex) {
        capture(clazz, level, message);
    }

    @Override
    public void log(Class<?> clazz, StackTraceElement caller, Log.Level level, String message, Throwable ex) {
        capture(clazz, level, message);
    }

    @Override
    public boolean isLoggable(Class<?> clazz, Log.Level level) {
        return true;
    }

    @Override
    public boolean needToInferCaller(Class<?> clazz, Log.Level level) {
        return false;
    }

    private static void capture(Class<?> clazz, Log.Level level, String message) {
        LINES.add(new CapturedLine(clazz.getName(), level, message));
    }
}
