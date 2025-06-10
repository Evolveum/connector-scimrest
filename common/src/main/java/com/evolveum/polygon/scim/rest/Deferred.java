package com.evolveum.polygon.scim.rest;

import org.glassfish.jersey.internal.util.Producer;

/**
 * Deferred value without blocking
 */
public abstract class Deferred<T> {

    public abstract T get() throws IllegalStateException;

    public static <T> Deferred<T> ready(T value) {
        return new Completed<>(value);
    }

    public static <T> Deferred.Settable<T> settable() {
        return new Settable<>();
    }

    public static <T> Deferred<T> searchable(Producer<T> lookup) {
        return new Searchable<>(lookup::call);
    }

    public static class Settable<T> extends Deferred<T> {

        T value;

        @Override
        public T get() throws IllegalStateException {
            if (value == null) {
                throw new IllegalStateException("Value has not been set yet.");
            }
            return value;
        }

        public void set(T value) {
            if (this.value != null) {
                throw new IllegalStateException("Value has already set.");
            }
            this.value = value;
        }
    }

    private static class Searchable<T> extends Deferred<T> {

        private Object value;

        Searchable(Search<T> value) {
            this.value = value;
        }

        @Override
        public T get() throws IllegalStateException {
            if (value instanceof Searchable) {
                value = ((Searchable<T>) value).get();
            }
            return (T) value;
        }
    }

    private static class Completed<T> extends Deferred<T> {

        T value;
        Completed(T value) {
            this.value = value;
        }

        @Override
        public T get() throws IllegalStateException {
            return value;
        }
    }

    interface Search<T> {

        T find();
    }
}
