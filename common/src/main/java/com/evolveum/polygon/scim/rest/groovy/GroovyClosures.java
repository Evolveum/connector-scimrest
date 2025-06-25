/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scim.rest.groovy;

import groovy.lang.Closure;

import java.util.function.Function;

public class GroovyClosures {

    /**
     * Calls the provided closure with the specified delegate and returns the delegate.
     *
     * @param <T>      The type of the delegate.
     * @param closure  The closure to be executed.
     * @param delegate The delegate object that will be used within the closure.
     * @return The modified delegate object after execution.
     */
    public static <T>  T callAndReturnDelegate(Closure<?> closure, T delegate) {
        closure.setDelegate(delegate);
        closure.setResolveStrategy(Closure.DELEGATE_FIRST);
        performClosureCall(closure, delegate);
        return delegate;
    }

    /**
     * Creates a copy of the provided closure and executes it with the specified delegate.
     *
     * @param <T> The type of the delegate.
     * @param prototype The original closure to be copied and executed.
     * @param delegate The delegate object that will be used within the closure.
     * @return The result of the closure execution.
     */
    public static <T>  T copyAndCall(Closure<T> prototype, Object delegate) {
        Closure<?> closure = (Closure<?>) prototype.clone();
        closure.setDelegate(delegate);
        closure.setResolveStrategy(Closure.DELEGATE_FIRST);
        // FIXME: Add type safety contracts
        return performClosureCall(closure, delegate);
    }

    private static <T> T performClosureCall(Closure<?> closure, Object delegate) {
        if (delegate instanceof ClosureExecutionAware executionAware) {
            executionAware.beforeExecution();
        }
        T ret = (T) closure.call();
        if (delegate instanceof ClosureExecutionAware executionAware) {
            executionAware.afterExecution();
        }
        return ret;
    }

    public static <T, R> Function<T, R> asFunction(Closure<R> closure) {
        return (T input) -> {
            var copy = (Closure<R>) closure.clone();
            return (R) copy.call(input);
        };

    }

    public interface ClosureExecutionAware {

        default void beforeExecution() {
            // Intentional NOOP
        }
        default void afterExecution() {
            // Intentional NOOP
        }
    }
}
