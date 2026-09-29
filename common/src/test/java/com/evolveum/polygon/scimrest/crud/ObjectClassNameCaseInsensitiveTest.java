/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.crud;

import com.evolveum.polygon.conndev.spi.ClassHandlerConnectorBase;
import com.evolveum.polygon.scimrest.groovy.impl.ManifestBasedConnector;
import com.evolveum.polygon.scimrest.support.AbstractCrudConnectorTest;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.ObjectClassInfo;
import org.identityconnectors.framework.common.objects.OperationOptionsBuilder;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.common.objects.filter.Filter;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

/**
 * Object class names are case-insensitive end to end: the generated YAML definitions of this
 * bundle name the object class {@code workpackage} (lowercase) in the schema <i>and</i> the list
 * script, while inbound calls arrive with {@code WorkPackage} — the spelling the schema presents
 * to the caller. The connector must dispatch such calls to the lowercase definitions and return
 * correct results, both for the {@code WorkPackage} call and, symmetrically, for a lowercase one.
 *
 * <p>The second fixture bundle mirrors the state the OpenProject connector ships in today: the
 * schema YAML documents use {@code WorkPackage} while the list script YAML uses {@code workpackage}
 * — a case mismatch <i>between</i> the YAML documents that must merge into one object class.
 */
public class ObjectClassNameCaseInsensitiveTest extends AbstractCrudConnectorTest {

    private static final String WORK_PACKAGES_PATH = "/work_packages";
    private static final Set<String> EXPECTED_IDS = Set.of("PROJ-1", "PROJ-2");
    private static final Set<String> EXPECTED_SUBJECTS = Set.of("First work package", "Second work package");

    /** All YAML definitions (schema and list script) name the object class {@code workpackage}. */
    private static class LowercaseWorkPackageConnector extends ManifestBasedConnector {
        LowercaseWorkPackageConnector() {
            super("/manifests/workpackage/connector.manifest");
        }
    }

    /** Schema YAML uses {@code WorkPackage}, the list script YAML uses {@code workpackage}. */
    private static class MixedCaseWorkPackageConnector extends ManifestBasedConnector {
        MixedCaseWorkPackageConnector() {
            super("/manifests/workpackage-mixed/connector.manifest");
        }
    }

    @Test
    public void schemaResolvesUppercaseObjectClassFromLowercaseYamlDefinitions() {
        var connector = init(new LowercaseWorkPackageConnector());

        var schema = connector.schema();

        var uppercase = schema.findObjectClassInfo("WorkPackage");
        assertNotNull(uppercase, "schema must resolve the uppercase object class name the caller uses");
        assertIdAttribute(uppercase);

        assertNotNull(schema.findObjectClassInfo("workpackage"),
                "schema must also resolve the lowercase spelling used by the YAML definitions");
        assertEquals(schema.getObjectClassInfo().size(), 1,
                "the case mismatch must not produce two object class definitions");
    }

    @Test
    public void searchListWithUppercaseObjectClassExecutesLowercaseYamlListScript() {
        stubWorkPackages();
        var connector = init(new LowercaseWorkPackageConnector());

        var results = search(connector, new ObjectClass("WorkPackage"), null);

        assertListedWorkPackages(results);
        wireMockServer.verify(getRequestedFor(urlEqualTo(WORK_PACKAGES_PATH)));
    }

    @Test
    public void searchListWithLowercaseObjectClassAlsoWorks() {
        stubWorkPackages();
        var connector = init(new LowercaseWorkPackageConnector());

        var results = search(connector, new ObjectClass("workpackage"), null);

        assertListedWorkPackages(results);
        wireMockServer.verify(getRequestedFor(urlEqualTo(WORK_PACKAGES_PATH)));
    }

    @Test
    public void mixedCaseBundleResolvesAndSearches() {
        stubWorkPackages();
        var connector = init(new MixedCaseWorkPackageConnector());

        var schema = connector.schema();
        assertNotNull(schema.findObjectClassInfo("WorkPackage"),
                "uppercase schema document must be visible under its own spelling");
        assertNotNull(schema.findObjectClassInfo("workpackage"),
                "uppercase schema document must be visible under the list script's spelling");
        assertEquals(schema.getObjectClassInfo().size(), 1,
                "case mismatch between the YAML documents must not produce two object classes");

        var results = search(connector, new ObjectClass("WorkPackage"), null);

        assertListedWorkPackages(results);
        wireMockServer.verify(getRequestedFor(urlEqualTo(WORK_PACKAGES_PATH)));
    }

    private ClassHandlerConnectorBase init(ManifestBasedConnector connector) {
        connector.init(new SimpleConfig(wireMockServer.port()));
        return connector;
    }

    /** The base class' {@code search} helpers hardcode the {@code Account} object class. */
    private List<ConnectorObject> search(ClassHandlerConnectorBase connector, ObjectClass objectClass, Filter filter) {
        var results = new ArrayList<ConnectorObject>();
        connector.executeQuery(objectClass, filter, o -> { results.add(o); return true; },
                new OperationOptionsBuilder().build());
        return results;
    }

    private void stubWorkPackages() {
        wireMockServer.stubFor(get(urlEqualTo(WORK_PACKAGES_PATH))
                .willReturn(okJson("""
                        {
                          "_embedded": {
                            "elements": [
                              {"id": "PROJ-1", "subject": "First work package"},
                              {"id": "PROJ-2", "subject": "Second work package"}
                            ]
                          },
                          "total": 2
                        }
                        """)));
    }

    private void assertListedWorkPackages(List<ConnectorObject> results) {
        assertEquals(results.size(), 2);
        assertEquals(results.stream().map(o -> o.getUid().getUidValue()).collect(Collectors.toSet()),
                EXPECTED_IDS);
        assertEquals(results.stream().map(o -> o.getName().getNameValue()).collect(Collectors.toSet()),
                EXPECTED_SUBJECTS);
    }

    private static void assertIdAttribute(ObjectClassInfo objectClassInfo) {
        var uid = objectClassInfo.getAttributeInfo().stream()
                .filter(a -> Uid.NAME.equals(a.getName()))
                .findFirst()
                .orElse(null);
        assertNotNull(uid, "workpackage object class has no UID attribute");
        assertEquals(uid.getNativeName(), "id");
        assertEquals(uid.getType(), String.class);
    }
}
