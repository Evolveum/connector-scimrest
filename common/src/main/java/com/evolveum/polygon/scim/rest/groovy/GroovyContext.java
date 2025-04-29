package com.evolveum.polygon.scim.rest.groovy;


import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovyShell;
import org.codehaus.groovy.control.CompilerConfiguration;

public class GroovyContext {

    private CompilerConfiguration compilerConfiguration;
    private GroovyClassLoader groovyClassLoader;

    public GroovyContext() {
        compilerConfiguration = new CompilerConfiguration();
        groovyClassLoader = new GroovyClassLoader(this.getClass().getClassLoader());
    }

    public GroovyShell createShell() {
        return  new GroovyShell(groovyClassLoader, compilerConfiguration);
    }


}
