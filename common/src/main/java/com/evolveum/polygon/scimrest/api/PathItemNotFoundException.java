package com.evolveum.polygon.scimrest.api;

public class PathItemNotFoundException extends ParsingException {


    private final AttributePath.Component missing;
    private final AttributePath resolved;

    public PathItemNotFoundException(AttributePath.Component missing, AttributePath resolved) {
        super("Attribute '" + missing + "' not found at path '" + resolved + "'.");
        this.missing = missing;
        this.resolved = resolved;
    }

    public PathItemNotFoundException(AttributePath.Component missing, AttributePath resolved, String string) {
        super("Attribute '" + missing + "' not found at path '" + resolved + "'. " + string);
        this.missing = missing;
        this.resolved = resolved;
    }

    public AttributePath getResolved() {
        return resolved;
    }

    public AttributePath.Component getMissing() {
        return missing;
    }

}
