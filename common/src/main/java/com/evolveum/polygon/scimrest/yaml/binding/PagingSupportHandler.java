/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.yaml.binding;

import com.evolveum.polygon.conndev.yaml.decl.GroovySyntaxChecker;
import com.evolveum.polygon.conndev.yaml.decl.LocatedNode;
import com.evolveum.polygon.conndev.yaml.decl.CustomYamlHandler;
import com.evolveum.polygon.conndev.yaml.decl.DeclYamlBinder;
import com.evolveum.polygon.scimrest.groovy.api.RestSearchEndpointBuilder;

/**
 * Binds a search endpoint's {@code pagingSupport:} key. Two shapes are accepted:
 *
 * <ul>
 *   <li>a mapping with a {@code pageSize} (the requested page size) and/or a {@code parameters}
 *       mapping of paging tokens ({@code pageSize}, {@code page}, {@code offset}) to
 *       {@code { in, name }} entries — a declarative paging handler; {@code name} defaults to
 *       the token;</li>
 *   <li>a string / block scalar — the Groovy form (a closure mapping the paging info onto the
 *       request).</li>
 * </ul>
 */
public class PagingSupportHandler implements CustomYamlHandler {

    @Override
    public void apply(DeclYamlBinder binder, Object target, LocatedNode value) {
        if (!(target instanceof RestSearchEndpointBuilder endpoint)) {
            throw new IllegalArgumentException("The 'pagingSupport' key requires a search endpoint, got: "
                    + target.getClass().getName());
        }
        if (value == null || value.isNull()) {
            return; // no paging support declared
        }
        if (value.kind() == LocatedNode.Kind.OBJECT) {
            for (var entry : value.entries()) {
                var entryValue = entry.value();
                switch (entry.key()) {
                    case "pageSize" -> {
                        if (!entryValue.isValue()) {
                            throw new IllegalArgumentException("The 'pagingSupport.pageSize' key requires a scalar value at "
                                    + entryValue.line() + ":" + entryValue.col());
                        }
                        int pageSize = entryValue.asInt();
                        if (pageSize < 1) {
                            throw new IllegalArgumentException(
                                    "The 'pagingSupport.pageSize' value must be at least 1, got " + pageSize
                                            + " at " + entryValue.line() + ":" + entryValue.col());
                        }
                        endpoint.pageSize(pageSize);
                    }
                    case "parameters" -> {
                        if (entryValue.kind() != LocatedNode.Kind.OBJECT) {
                            throw new IllegalArgumentException("The 'pagingSupport.parameters' key requires a mapping of paging"
                                    + " tokens (pageSize / page / offset) at "
                                    + entryValue.line() + ":" + entryValue.col());
                        }
                        for (var token : entryValue.entries()) {
                            var tokenValue = token.value();
                            if (tokenValue.kind() != LocatedNode.Kind.OBJECT) {
                                throw new IllegalArgumentException("The paging token '" + token.key() + "' requires a mapping"
                                        + " ('in' and optional 'name') at " + tokenValue.line() + ":" + tokenValue.col());
                            }
                            String location = requireScalar(tokenValue, "in", tokenValue);
                            var nameNode = tokenValue.get("name");
                            String name;
                            if (nameNode == null) {
                                name = token.key();
                            } else if (!nameNode.isValue()) {
                                throw new IllegalArgumentException("The 'name' key of paging token '" + token.key()
                                        + "' requires a scalar value at " + nameNode.line() + ":" + nameNode.col());
                            } else {
                                name = nameNode.text();
                            }
                            for (var extra : tokenValue.entries()) {
                                if (!"in".equals(extra.key()) && !"name".equals(extra.key())) {
                                    throw new IllegalArgumentException("Unknown key '" + extra.key() + "' of paging token '"
                                            + token.key() + "' (expected 'in' and optional 'name') at "
                                            + extra.keyLine() + ":" + extra.keyCol());
                                }
                            }
                            endpoint.pagingParameter(token.key(), location, name);
                        }
                    }
                    default -> throw new IllegalArgumentException(
                            "Unknown key '" + entry.key() + "' in 'pagingSupport' (expected 'pageSize' or 'parameters') at "
                                    + entry.keyLine() + ":" + entry.keyCol());
                }
            }
            return;
        }
        if (value.isValue()) {
            endpoint.pagingSupport(binder.compileClosure(value.text()));
            return;
        }
        throw new IllegalArgumentException("The 'pagingSupport' key requires a mapping (pageSize / parameters)"
                + " or a Groovy block at " + value.line() + ":" + value.col());
    }

    @Override
    public void checkGroovySyntax(LocatedNode value, String path, GroovySyntaxChecker checker) {
        // Only the string form carries Groovy.
        if (value != null && value.kind() == LocatedNode.Kind.SCALAR) {
            checker.checkFragment(value, path);
        }
    }

    private static String requireScalar(LocatedNode map, String key, LocatedNode context) {
        var node = map.get(key);
        if (node == null || !node.isValue()) {
            throw new IllegalArgumentException("Missing required key '" + key + "' of a paging token declaration at "
                    + context.line() + ":" + context.col());
        }
        return node.text();
    }
}
