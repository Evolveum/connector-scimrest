/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.groovy;

import java.lang.annotation.*;

/**
 * Annotation for categorizing Groovy closures in SCIM REST connector framework.
 * Used to distinguish between initialization-time and runtime closures.
 */
public @interface Script {
    
    /**
     * Closure is executed immediately during configuration/initialization phase.
     * Usually delegates to builder methods and configures the builder.
     * Execution happens in the context where it's defined.
     */
    @Retention(RetentionPolicy.CLASS)
    @Target(ElementType.PARAMETER)
    @interface Initialization {
    }
    
    /**
     * Closure is wrapped and executed at runtime during operation execution.
     * Stored as prototype and cloned/executed later via GroovyClosures.copyAndCall().
     * Execution happens in runtime context (e.g., during search, attribute resolution).
     */
    @Retention(RetentionPolicy.CLASS)
    @Target(ElementType.PARAMETER)
    @interface Runtime {
    }
}
