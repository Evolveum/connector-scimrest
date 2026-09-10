/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.logging;

import org.slf4j.ILoggerFactory;
import org.slf4j.IMarkerFactory;
import org.slf4j.Logger;
import org.slf4j.Marker;
import org.slf4j.helpers.BasicMarkerFactory;
import org.slf4j.helpers.MessageFormatter;
import org.slf4j.spi.MDCAdapter;
import org.slf4j.spi.SLF4JServiceProvider;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * In-memory {@link SLF4JServiceProvider} for tests: every logged message is appended to a
 * static queue together with the name of the logger it was emitted through, so tests can
 * observe both the lines a component emitted and which logger emitted them. Registered via
 * {@code META-INF/services/org.slf4j.spi.SLF4JServiceProvider}.
 *
 * <p>Every level is enabled and nothing is filtered, so tests observe all calls; callers must
 * {@link #clear()} the captured lines before exercising a component.
 */
public final class CapturingLogProvider implements SLF4JServiceProvider {

    /**
     * A captured log line.
     *
     * @param logger  the name of the logger the line was emitted through
     * @param message the formatted message
     */
    public record CapturedLine(String logger, String message) {
    }

    private static final ConcurrentLinkedQueue<CapturedLine> LINES = new ConcurrentLinkedQueue<>();

    private final ILoggerFactory loggerFactory = name -> new CapturingLogger(name);
    private final IMarkerFactory markerFactory = new BasicMarkerFactory();
    private final MDCAdapter mdcAdapter = new MDCAdapter() {
        @Override
        public void put(String key, String val) {
        }

        @Override
        public String get(String key) {
            return null;
        }

        @Override
        public void remove(String key) {
        }

        @Override
        public void clear() {
        }

        @Override
        public Map<String, String> getCopyOfContextMap() {
            return Map.of();
        }

        @Override
        public void setContextMap(Map<String, String> contextMap) {
        }

        @Override
        public void pushByKey(String key, String value) {
        }

        @Override
        public String popByKey(String key) {
            return null;
        }

        @Override
        public Deque<String> getCopyOfDequeByKey(String key) {
            return new ArrayDeque<>();
        }

        @Override
        public void clearDequeByKey(String key) {
        }
    };

    /**
     * Returns the log lines captured so far, in emission order.
     *
     * @return the captured lines
     */
    public static List<CapturedLine> lines() {
        return List.copyOf(LINES);
    }

    /** Removes all captured lines. */
    public static void clear() {
        LINES.clear();
    }

    @Override
    public ILoggerFactory getLoggerFactory() {
        return loggerFactory;
    }

    @Override
    public IMarkerFactory getMarkerFactory() {
        return markerFactory;
    }

    @Override
    public MDCAdapter getMDCAdapter() {
        return mdcAdapter;
    }

    @Override
    public String getRequestedApiVersion() {
        return "2.0.18";
    }

    @Override
    public void initialize() {
        // nothing to initialize
    }

    private static final class CapturingLogger implements Logger {

        private final String name;

        private CapturingLogger(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public boolean isTraceEnabled() {
            return true;
        }

        @Override
        public boolean isTraceEnabled(Marker marker) {
            return true;
        }

        @Override
        public void trace(String msg) {
            capture(msg);
        }

        @Override
        public void trace(String format, Object arg) {
            capture(format, new Object[]{arg});
        }

        @Override
        public void trace(String format, Object arg1, Object arg2) {
            capture(format, new Object[]{arg1, arg2});
        }

        @Override
        public void trace(String format, Object... arguments) {
            capture(format, arguments);
        }

        @Override
        public void trace(String msg, Throwable t) {
            capture(msg);
        }

        @Override
        public void trace(Marker marker, String msg) {
            capture(msg);
        }

        @Override
        public void trace(Marker marker, String format, Object arg) {
            capture(format, new Object[]{arg});
        }

        @Override
        public void trace(Marker marker, String format, Object arg1, Object arg2) {
            capture(format, new Object[]{arg1, arg2});
        }

        @Override
        public void trace(Marker marker, String format, Object... arguments) {
            capture(format, arguments);
        }

        @Override
        public void trace(Marker marker, String msg, Throwable t) {
            capture(msg);
        }

        @Override
        public boolean isDebugEnabled() {
            return true;
        }

        @Override
        public boolean isDebugEnabled(Marker marker) {
            return true;
        }

        @Override
        public void debug(String msg) {
            capture(msg);
        }

        @Override
        public void debug(String format, Object arg) {
            capture(format, new Object[]{arg});
        }

        @Override
        public void debug(String format, Object arg1, Object arg2) {
            capture(format, new Object[]{arg1, arg2});
        }

        @Override
        public void debug(String format, Object... arguments) {
            capture(format, arguments);
        }

        @Override
        public void debug(String msg, Throwable t) {
            capture(msg);
        }

        @Override
        public void debug(Marker marker, String msg) {
            capture(msg);
        }

        @Override
        public void debug(Marker marker, String format, Object arg) {
            capture(format, new Object[]{arg});
        }

        @Override
        public void debug(Marker marker, String format, Object arg1, Object arg2) {
            capture(format, new Object[]{arg1, arg2});
        }

        @Override
        public void debug(Marker marker, String format, Object... arguments) {
            capture(format, arguments);
        }

        @Override
        public void debug(Marker marker, String msg, Throwable t) {
            capture(msg);
        }

        @Override
        public boolean isInfoEnabled() {
            return true;
        }

        @Override
        public boolean isInfoEnabled(Marker marker) {
            return true;
        }

        @Override
        public void info(String msg) {
            capture(msg);
        }

        @Override
        public void info(String format, Object arg) {
            capture(format, new Object[]{arg});
        }

        @Override
        public void info(String format, Object arg1, Object arg2) {
            capture(format, new Object[]{arg1, arg2});
        }

        @Override
        public void info(String format, Object... arguments) {
            capture(format, arguments);
        }

        @Override
        public void info(String msg, Throwable t) {
            capture(msg);
        }

        @Override
        public void info(Marker marker, String msg) {
            capture(msg);
        }

        @Override
        public void info(Marker marker, String format, Object arg) {
            capture(format, new Object[]{arg});
        }

        @Override
        public void info(Marker marker, String format, Object arg1, Object arg2) {
            capture(format, new Object[]{arg1, arg2});
        }

        @Override
        public void info(Marker marker, String format, Object... arguments) {
            capture(format, arguments);
        }

        @Override
        public void info(Marker marker, String msg, Throwable t) {
            capture(msg);
        }

        @Override
        public boolean isWarnEnabled() {
            return true;
        }

        @Override
        public boolean isWarnEnabled(Marker marker) {
            return true;
        }

        @Override
        public void warn(String msg) {
            capture(msg);
        }

        @Override
        public void warn(String format, Object arg) {
            capture(format, new Object[]{arg});
        }

        @Override
        public void warn(String format, Object arg1, Object arg2) {
            capture(format, new Object[]{arg1, arg2});
        }

        @Override
        public void warn(String format, Object... arguments) {
            capture(format, arguments);
        }

        @Override
        public void warn(String msg, Throwable t) {
            capture(msg);
        }

        @Override
        public void warn(Marker marker, String msg) {
            capture(msg);
        }

        @Override
        public void warn(Marker marker, String format, Object arg) {
            capture(format, new Object[]{arg});
        }

        @Override
        public void warn(Marker marker, String format, Object arg1, Object arg2) {
            capture(format, new Object[]{arg1, arg2});
        }

        @Override
        public void warn(Marker marker, String format, Object... arguments) {
            capture(format, arguments);
        }

        @Override
        public void warn(Marker marker, String msg, Throwable t) {
            capture(msg);
        }

        @Override
        public boolean isErrorEnabled() {
            return true;
        }

        @Override
        public boolean isErrorEnabled(Marker marker) {
            return true;
        }

        @Override
        public void error(String msg) {
            capture(msg);
        }

        @Override
        public void error(String format, Object arg) {
            capture(format, new Object[]{arg});
        }

        @Override
        public void error(String format, Object arg1, Object arg2) {
            capture(format, new Object[]{arg1, arg2});
        }

        @Override
        public void error(String format, Object... arguments) {
            capture(format, arguments);
        }

        @Override
        public void error(String msg, Throwable t) {
            capture(msg);
        }

        @Override
        public void error(Marker marker, String msg) {
            capture(msg);
        }

        @Override
        public void error(Marker marker, String format, Object arg) {
            capture(format, new Object[]{arg});
        }

        @Override
        public void error(Marker marker, String format, Object arg1, Object arg2) {
            capture(format, new Object[]{arg1, arg2});
        }

        @Override
        public void error(Marker marker, String format, Object... arguments) {
            capture(format, arguments);
        }

        @Override
        public void error(Marker marker, String msg, Throwable t) {
            capture(msg);
        }

        private void capture(String format, Object... arguments) {
            capture(MessageFormatter.arrayFormat(format, arguments).getMessage());
        }

        private void capture(String formattedMessage) {
            LINES.add(new CapturedLine(name, formattedMessage));
        }
    }
}
