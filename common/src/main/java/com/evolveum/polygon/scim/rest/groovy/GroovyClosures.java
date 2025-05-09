package com.evolveum.polygon.scim.rest.groovy;

import groovy.lang.Closure;

public class GroovyClosures {
    public static <T>  T callAndReturnDelegate(Closure<?> closure, T delegate) {
        closure.setDelegate(delegate);
        closure.setResolveStrategy(Closure.DELEGATE_FIRST);
        closure.call();
        return delegate;
    }

    public static <T>  T copyAndCall(Closure<?> prototype, Object delegate) {
        Closure<?> closure = (Closure<?>) prototype.clone();
        closure.setDelegate(delegate);
        closure.setResolveStrategy(Closure.DELEGATE_FIRST);
        // FIXME: Add type safety contracts
        return (T) closure.call();
    }
}
