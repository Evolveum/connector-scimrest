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
Maps one multivalued ConnId attribute to separate REST relationship records. The updated object's UID
is the source identifier; attribute values or reference UIDs are target identifiers. GET reads the
collection, POST creates records, and DELETE addresses individual record IDs. These operations are
not transactional. Without paging, GET must return the complete collection.
*/
public interface RelationshipRecordsBuilder {

    /**
    Sets the GET/POST collection path. The optional {@code {source}} placeholder contains the encoded source UID.
    */
    @Yaml.Key
    RelationshipRecordsBuilder endpoint(String path);

    /**
    Sets the DELETE path, containing {@code {id}} and optionally {@code {source}}. Defaults to endpoint/{id}.
    */
    @Yaml.Key
    RelationshipRecordsBuilder deleteEndpoint(String path);

    /**
    Maps the source identifier to an object field used for reading and creating records.
    Accepts a bare field name or a basic JSONPath expression; structured declarations also support JSON Pointer.
    */
    @Yaml.Path
    RelationshipRecordsBuilder sourceField(AttributePathDeclaration<?, ?> path);

    default RelationshipRecordsBuilder sourceField(String path) {
        return sourceField(AttributePathDeclaration.of(BasicJsonPathFormat.INSTANCE, path));
    }

    /**
    Maps the target identifier to an object field used for reading and creating records.
    Accepts the same path forms as sourceField; source, target, and record identifier paths must not overlap.
    */
    @Yaml.Path
    RelationshipRecordsBuilder targetField(AttributePathDeclaration<?, ?> path);

    default RelationshipRecordsBuilder targetField(String path) {
        return targetField(AttributePathDeclaration.of(BasicJsonPathFormat.INSTANCE, path));
    }

    /**
    Selects each record's identifier for DELETE; this is independent of the target identifier.
    */
    @Yaml.Path
    RelationshipRecordsBuilder idField(AttributePathDeclaration<?, ?> path);

    default RelationshipRecordsBuilder idField(String path) {
        return idField(AttributePathDeclaration.of(BasicJsonPathFormat.INSTANCE, path));
    }

    /**
    Selects the record array in a wrapped GET response. Omit when the response root is an array.
    */
    @Yaml.Path
    RelationshipRecordsBuilder recordsPath(AttributePathDeclaration<?, ?> path);

    default RelationshipRecordsBuilder recordsPath(String path) {
        return recordsPath(AttributePathDeclaration.of(BasicJsonPathFormat.INSTANCE, path));
    }

    /**
    Adds the source UID as a GET query parameter. Returned records are still checked for source ownership.
    */
    @Yaml.Key
    RelationshipRecordsBuilder sourceParameter(String name);

    /**
    Sets source JSON representation to {@code string} (default) or {@code integer}.
    */
    @Yaml.Key
    RelationshipRecordsBuilder sourceType(String type);

    /**
    Sets target JSON representation to {@code string} (default) or {@code integer}.
    */
    @Yaml.Key
    RelationshipRecordsBuilder targetType(String type);

    /**
    Enables offset paging with a positive requested page size. Zero disables paging. Reading continues
    until an empty page, including after short pages; every page is validated before any mutation.
    */
    @Yaml.Key
    RelationshipRecordsBuilder pageSize(int size);

    /**
    Sets the offset query parameter name; defaults to {@code offset}.
    */
    @Yaml.Key
    RelationshipRecordsBuilder offsetParameter(String name);

    /**
    Sets the page-size query parameter name; defaults to {@code limit}.
    */
    @Yaml.Key
    RelationshipRecordsBuilder limitParameter(String name);

    /**
    Sets the first offset; defaults to zero. Subsequent offsets advance by the number of returned records.
    */
    @Yaml.Key
    RelationshipRecordsBuilder initialOffset(int offset);

    /**
    Limits collection requests when paging is enabled; defaults to 1000. Reaching the limit before an empty page fails the update.
    */
    @Yaml.Key
    RelationshipRecordsBuilder maxPages(int count);
}
