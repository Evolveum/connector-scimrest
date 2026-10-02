package com.evolveum.polygon.scimrest.schema;

/**
 * Declaration of a value lookup from one object class's attribute to a
 * {@code cached} object class.
 *
 * <p>{@code serialize} names the attribute of the cached class whose value is the
 * native/remote representation (e.g. {@code href}), {@code deserialize} names the
 * attribute whose value is the human-facing representation (e.g. {@code name}).
 */

public record RestLookupMapping(String objectClass, String serialize, String deserialize) {
}