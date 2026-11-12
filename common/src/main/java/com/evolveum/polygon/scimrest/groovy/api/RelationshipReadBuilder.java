/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 */
package com.evolveum.polygon.scimrest.groovy.api;

import com.evolveum.polygon.conndev.annotations.Yaml;
import com.evolveum.polygon.conndev.api.AttributePathDeclaration;
import com.evolveum.polygon.conndev.api.BasicJsonPathFormat;

/**
Reads a multivalued reference attribute from separate, UID-scoped membership records. The target object class comes from the attribute schema.
Offset pagination continues until an empty page, including after short pages; the attribute is published only after the complete collection is read.
*/
public interface RelationshipReadBuilder extends PagingSupportBuilder<RelationshipReadBuilder> {

    /**
    Sets the GET collection path. The optional {@code {source}} placeholder contains the source UID encoded as one URI path segment.
    */
    @Yaml.Key
    RelationshipReadBuilder endpoint(String path);

    /**
    Adds the current object's UID to this query parameter on every request. Required unless the endpoint contains {@code {source}}.
    */
    @Yaml.Key
    RelationshipReadBuilder sourceParameter(String name);

    /**
    Sets the query value template, containing {@code {uid}}. Defaults to {@code {uid}}; the resulting query value is URL-encoded.
    Only {@code {uid}} is substituted; literal braces, including those in JSON filters, are preserved.
    Substitution is literal, so the UID must satisfy the target API's filter syntax when the template contains a filter expression.
    */
    @Yaml.Key
    RelationshipReadBuilder sourceTemplate(String template);

    /**
    Selects the membership array inside a wrapped response. Omit for a root array. Accepts a bare field name, basic JSONPath, or structured JSON Pointer.
    */
    @Yaml.Path
    RelationshipReadBuilder objectsPath(AttributePathDeclaration<?, ?> path);

    default RelationshipReadBuilder objectsPath(String path) {
        return objectsPath(AttributePathDeclaration.of(BasicJsonPathFormat.INSTANCE, path));
    }

    /**
    Selects each record's target UID, which must be a nonblank string or an integer. Accepts the same path forms as objectsPath.
    */
    @Yaml.Path
    RelationshipReadBuilder targetField(AttributePathDeclaration<?, ?> path);

    default RelationshipReadBuilder targetField(String path) {
        return targetField(AttributePathDeclaration.of(BasicJsonPathFormat.INSTANCE, path));
    }

    /**
    Optionally selects the record's source UID. Records belonging to other objects are skipped; missing or invalid source identifiers fail the read.
    */
    @Yaml.Path
    RelationshipReadBuilder sourceField(AttributePathDeclaration<?, ?> path);

    default RelationshipReadBuilder sourceField(String path) {
        return sourceField(AttributePathDeclaration.of(BasicJsonPathFormat.INSTANCE, path));
    }

    /**
    Sets the positive requested page size; defaults to 100. Pagination advances by the actual number of records returned, including foreign records.
    */
    @Override
    RelationshipReadBuilder pageSize(int size);

    /**
    Declares offset and pageSize parameters using the shared paging syntax. Both are required; this GET reader supports query parameters and headers.
    */
    @Override
    RelationshipReadBuilder pagingParameter(String token, String location, String name);

    /**
    Sets the first record offset; defaults to zero.
    */
    @Yaml.Key
    RelationshipReadBuilder initialOffset(int offset);

    /**
    Limits collection requests, including the final empty page; defaults to 1000. Reaching the limit before an empty page fails the read.
    */
    @Yaml.Key
    RelationshipReadBuilder maxPages(int count);
}
