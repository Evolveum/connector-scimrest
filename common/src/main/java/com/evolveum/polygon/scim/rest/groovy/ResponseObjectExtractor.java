/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scim.rest.groovy;


import java.net.http.HttpResponse;

/**
 * Functional interface for extracting objects from HTTP responses.
 *
 * This interface defines a single method, `extractObjects`, which takes an HTTP response and returns an iterable of objects
 * extracted from it. It is intended to be implemented by classes that know how to parse the content of body into Java objects.
 *
 * Implementations of this interface can be used in scenarios where HTTP response does not contains list as top level item,
 * but actual list of objects needs to be extracted from deeper structures.
 * @param <BF> The type of the base object returned by the HTTP response.
 * @param <OF> The type of objects that will be extracted from the HTTP response.
 */
@FunctionalInterface
public interface ResponseObjectExtractor<BF, OF> {

    Iterable<OF> extractObjects( HttpResponse<BF> response);

}
