/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.groovy;


import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovyShell;
import org.codehaus.groovy.control.CompilerConfiguration;

public class GroovyContext {

    private final CompilerConfiguration compilerConfiguration;
    private final GroovyClassLoader groovyClassLoader;

    public GroovyContext() {
        compilerConfiguration = new CompilerConfiguration();
        groovyClassLoader = new GroovyClassLoader(this.getClass().getClassLoader());
    }

    public GroovyShell createShell() {
        return  new GroovyShell(groovyClassLoader, compilerConfiguration);
    }


}
