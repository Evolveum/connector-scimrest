/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.lookup;

import com.evolveum.polygon.scimrest.schema.RestLookupMapping;
import org.identityconnectors.framework.common.exceptions.ConfigurationException;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Per-connector-instance cache of one {@code cached} object class, reduced to the two
 * attributes a configured lookup needs.
 *
 * <p>Initialization is lazy (first create/update that actually needs a lookup) and
 * all-or-nothing: the table is published via a single atomic {@code set} only after the
 * full search succeeded, so a failed or partial load leaves {@code table == null} and the
 * next operation retries. After a successful load, no further search is issued for this
 * class on this connector instance.
 */
public final class CachedObjectLookupResolver {

    private static final Logger LOG = LoggerFactory.getLogger(CachedObjectLookupResolver.class);

    public interface SearchExecutor {
        List<ConnectorObject> searchAll(String objectClassName);
    }

    private record Entry(Map<String, String> toUser, Map<String, String> toNative) {
    }

    private final RestLookupMapping mapping;
    private final SearchExecutor searchExecutor;
    private final Object initLock = new Object();
    private final AtomicReference<Entry> table = new AtomicReference<>();
    private final ThreadLocal<Set<String>> loading = ThreadLocal.withInitial(HashSet::new);
    private static final Entry NO_ENTRY = new Entry(Map.of(), Map.of());

    public CachedObjectLookupResolver(RestLookupMapping mapping, SearchExecutor searchExecutor) {
        this.mapping = mapping;
        this.searchExecutor = searchExecutor;
    }

    public String toNative(Object value) {
        if (value == null) {
            return null;
        }
        var entry = ensureLoaded();
        var nativ = entry.toNative().get(String.valueOf(value));
        if (nativ == null) {
            throw new ConnectorException("Unknown value '" + value + "' for lookup to object class '"
                    + mapping.objectClass() + "', known values: " + entry.toNative().keySet());
        }
        return nativ;
    }

    public String toUser(Object value) {
        if (value == null) {
            return null;
        }
        var entry = ensureLoaded();
        var user = entry.toUser().get(String.valueOf(value));
        if (user == null) {
            LOG.warn("No lookup value for native '{}' of cached object class '{}', passing raw value through",
                    value, mapping.objectClass());
            return String.valueOf(value);
        }
        return user;
    }

    private Entry ensureLoaded() {
        var entry = table.get();
        if (entry != null) {
            return entry;
        }
        var name = mapping.objectClass();
        if (!loading.get().add(name)) {
            LOG.warn("Lookup cycle detected at object class '{}', passing values through", name);
            return NO_ENTRY;
        }
        try {
            synchronized (initLock) {
                entry = table.get();
                if (entry == null) {
                    entry = load();
                    table.set(entry);
                }
                return entry;
            }
        } finally {
            loading.get().remove(name);
        }
    }

    private Entry load() {
        var toUser = new HashMap<String, String>();
        var toNative = new HashMap<String, String>();
        for (var co : searchExecutor.searchAll(mapping.objectClass())) {
            var nativ = singleStringValue(co, mapping.serialize());
            var user = singleStringValue(co, mapping.deserialize());
            if (nativ == null || user == null) {
                LOG.warn("Skipping cached object '{}' in '{}': null/missing/multi-valued '{}' or '{}'",
                        co.getUid(), mapping.objectClass(), mapping.serialize(), mapping.deserialize());
                continue;
            }
            if (toNative.put(user, nativ) != null) {
                throw new ConfigurationException("Duplicate lookup value '" + nativ + "' in cached object class '"
                        + mapping.objectClass() + "' (attribute '" + mapping.serialize() + "'), lookup cannot be initialized");
            }
            if (toUser.put(nativ, user) != null) {
                throw new ConfigurationException("Duplicate lookup value '" + user + "' in cached object class '"
                        + mapping.objectClass() + "' (attribute '" + mapping.deserialize() + "'), lookup cannot be initialized");
            }
        }
        if (toUser.isEmpty()) {
            LOG.warn("Cached object class '{}' resolved to an EMPTY lookup table (attribute '{}' x '{}'). "
                            + "Check the search operation / extraction for object class '{}'.",
                    mapping.objectClass(), mapping.serialize(), mapping.deserialize(), mapping.objectClass());
        }
        return new Entry(Map.copyOf(toUser), Map.copyOf(toNative));
    }


    private static String singleStringValue(ConnectorObject co, String name) {
        for (var attr : co.getAttributes()) {
            if (name.equals(attr.getName()) && attr.getValue().size() == 1) {
                return String.valueOf(attr.getValue().get(0));
            }
        }
        return null;
    }
}