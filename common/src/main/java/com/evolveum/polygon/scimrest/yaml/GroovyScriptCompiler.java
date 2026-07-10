/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.yaml;

import com.evolveum.polygon.scimrest.groovy.GroovyContext;
import groovy.lang.Closure;

/**
 * Compiles Groovy block scalars carried by the YAML documents into {@link Closure}s, so imperative
 * logic keeps working under the declarative YAML envelope. The snippet is wrapped in a closure
 * literal and evaluated on the shared {@link GroovyContext} shell, so it sees the same imports and
 * bindings as the Groovy DSL scripts. (The conndev {@code GroovyScriptCompiler} is the same concept
 * bound to the conndev {@code GroovyContext} — unifiable once the model fork is gone.)
 */
final class GroovyScriptCompiler {

    private final GroovyContext groovyContext;

    GroovyScriptCompiler(GroovyContext groovyContext) {
        this.groovyContext = groovyContext;
    }

    Closure<?> compile(String groovySource) {
        return (Closure<?>) groovyContext.createShell().evaluate("{ ->\n" + groovySource + "\n}");
    }

    /** Compiles a snippet whose single argument is available under {@code parameterName}. */
    Closure<?> compile(String groovySource, String parameterName) {
        return (Closure<?>) groovyContext.createShell().evaluate("{ " + parameterName + " ->\n" + groovySource + "\n}");
    }

    /** Evaluates a snippet immediately with the given delegate (used for filter {@code spec}s). */
    Object evaluate(String groovySource, Object delegate) {
        Closure<?> closure = compile(groovySource);
        closure.setDelegate(delegate);
        closure.setResolveStrategy(Closure.DELEGATE_FIRST);
        return closure.call();
    }
}
