package com.evolveum.polygon.scim.rest.groovy;

import groovy.lang.Closure;

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
        closure.call();
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
    public static <T>  T copyAndCall(Closure<?> prototype, Object delegate) {
        Closure<?> closure = (Closure<?>) prototype.clone();
        closure.setDelegate(delegate);
        closure.setResolveStrategy(Closure.DELEGATE_FIRST);
        // FIXME: Add type safety contracts
        return (T) closure.call();
    }
}
