/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.yaml;

import com.evolveum.polygon.conndev.yaml.YamlDocuments;
import com.evolveum.polygon.scimrest.yaml.model.YamlOperationDocument;

import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Loader of the operations YAML documents — the declarative counterpart of the Groovy operation and
 * authentication scripts. Documents are deserialized by Jackson into the typed
 * {@link YamlOperationDocument} model (unknown keys fail fast) and collected as parsed; nothing is
 * derived from them yet — the functional handlers are still built solely by the Groovy scripts.
 * Binding the documents onto {@code RestHandlerBuilder} is the next phase (see
 * {@code .tasks/in-progress/yaml-operations-inert-loading.txt}).
 */
public class YamlOperationLoader {

    private final List<YamlOperationDocument> documents = new ArrayList<>();

    public void load(String yaml) {
        add(YamlDocuments.readSingle(yaml, YamlOperationDocument.class, "operations document", "inline document"), "inline document");
    }

    public void load(Reader reader, String sourceName) {
        add(YamlDocuments.readSingle(reader, YamlOperationDocument.class, "operations document", sourceName), sourceName);
    }

    private void add(YamlOperationDocument document, String sourceName) {
        validate(document, sourceName);
        documents.add(document);
    }

    /**
     * One file describes exactly one operation, mirroring the Groovy convention
     * ({@code User.search.groovy}, {@code authorization.op.groovy}): a document carries exactly one
     * of the {@code search}/{@code create}/{@code update}/{@code authentication} sections, and the
     * operation sections require {@code objectClass}.
     */
    private void validate(YamlOperationDocument document, String sourceName) {
        long sections = Stream.of(document.search, document.create, document.update, document.authentication)
                .filter(Objects::nonNull)
                .count();
        if (sections != 1) {
            throw new IllegalArgumentException("Operations YAML document must declare exactly one operation"
                    + " (search/create/update/authentication), found " + sections + " (" + sourceName + ")");
        }
        boolean isOperation = document.authentication == null;
        if (isOperation && (document.objectClass == null || document.objectClass.isBlank())) {
            throw new IllegalArgumentException(
                    "Operations YAML document declares an operation but no objectClass (" + sourceName + ")");
        }
    }

    /** All documents loaded so far, in loading order. */
    public List<YamlOperationDocument> documents() {
        return Collections.unmodifiableList(documents);
    }
}
