package com.evolveum.polygon.scimrest.api;

import java.util.Objects;
import java.util.function.Supplier;

public class ParsingException extends RuntimeException {

    private String context;

    public ParsingException(String message) {
        super(message);
    }

    public ParsingException(String message, Throwable cause) {
        super(message, cause);
    }

    public ParsingException(Throwable cause) {
        super(cause);
    }

    public void addContext(String body) {
        this.context = body;
    }

    public String getContext() {
        return context;
    }

    public ParsingException withContext(Object body) {
        if (context == null) {
            this.context = Objects.toString(body);
        }
        return this;
    }
}
