/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.groovy;

import java.net.http.HttpResponse;


/**
 * Functional interface for extracting a total count of objects from an HTTP response.
 * This allows for flexible and concise logic to be defined declaratively.
 *
 * Implementations of this interface should provide a method that takes an HttpResponse object as input and returns
 * the total count extracted from it. The total count should represent number of all objects (in case of paging, it is not number of objects in result).
 *
 * If the total number of objects can not be determined the implementation should return `null`.
 *
 */
@FunctionalInterface
public interface TotalCountExtractor<T> {

    public static final TotalCountExtractor<?> UNSUPPORTED = (r) -> null;
    public static final TotalCountExtractor<?> SINGLE_OBJECT = (r) -> {
        if (r.statusCode() == 200) {
            return 1;
        } else {
            return 0;
        }
    };

    /**
     * Returns a TotalCountExtractor that
     *
     * @return A TotalCountExtractor that always returns `null`.
     */
    @SuppressWarnings("unchecked")
    static <BF> TotalCountExtractor<BF> unsupported() {
        return (TotalCountExtractor<BF>) UNSUPPORTED;
    }

    /**
     * Returns a TotalCountExtractor that always returns `1` for any HttpResponse object,
     * regardless of its content, it is useful for APIs where always one object is returned.
     *
     * @param <BF> the type of the response body
     * @return a TotalCountExtractor instance that counts objects as 1
     */
    @SuppressWarnings("unchecked")
    static <BF> TotalCountExtractor<BF> singleObject() {
        return (TotalCountExtractor<BF>) SINGLE_OBJECT;
    }


    /**
     * Extracts the total count of all objects from an HTTP response.
     *
     * In case of HTTP responses from paging APIs this should return total count of all objects,
     * not object per page.
     *
     * If the total count can not be determined return null.
     *
     * @param response The HttpResponse object containing the data to be processed.
     * @return The total count extracted from the response, or null if it cannot be determined.
     */
    Integer extractTotalCount(HttpResponse<T> response);


}
