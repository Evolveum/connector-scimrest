/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.unit.scim;

import com.evolveum.polygon.scimrest.impl.scim.ScimFilterTranslator;
import com.evolveum.polygon.scimrest.schema.ScimPathFormat;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.BaseObject;
import org.identityconnectors.framework.common.objects.filter.*;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

public class ScimFilterTranslatorTest {

    private static final String ENTERPRISE_USER_URI = "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User";

    private final ScimFilterTranslator translator = new ScimFilterTranslator(Map.of(
            "userName", ScimPathFormat.INSTANCE.parse("userName"),
            "uid", ScimPathFormat.INSTANCE.parse("id"),
            "name", ScimPathFormat.INSTANCE.parse("userName"),
            "active", ScimPathFormat.INSTANCE.parse("active"),
            "age", ScimPathFormat.INSTANCE.parse("age"),
            "givenName", ScimPathFormat.INSTANCE.parse("name.givenName"),
            "employeeNumber", ScimPathFormat.INSTANCE.parse(ENTERPRISE_USER_URI + ":employeeNumber"),
            "workEmail", ScimPathFormat.INSTANCE.parse("emails[type eq \"work\"].value")));

    // ==================== comparison operators ====================

    @Test
    public void equalsFilterIsTranslatedToEq() {
        var filter = new EqualsFilter(AttributeBuilder.build("userName", "jdoe"));

        assertEquals(translator.translate(filter), "userName eq \"jdoe\"");
    }

    @Test
    public void containsFilterIsTranslatedToCo() {
        var filter = new ContainsFilter(AttributeBuilder.build("userName", "do"));

        assertEquals(translator.translate(filter), "userName co \"do\"");
    }

    @Test
    public void startsWithFilterIsTranslatedToSw() {
        var filter = new StartsWithFilter(AttributeBuilder.build("userName", "J"));

        assertEquals(translator.translate(filter), "userName sw \"J\"");
    }

    @Test
    public void endsWithFilterIsTranslatedToEw() {
        var filter = new EndsWithFilter(AttributeBuilder.build("userName", "oe"));

        assertEquals(translator.translate(filter), "userName ew \"oe\"");
    }

    @Test
    public void greaterThanFilterIsTranslatedToGt() {
        var filter = new GreaterThanFilter(AttributeBuilder.build("age", 30));

        assertEquals(translator.translate(filter), "age gt 30");
    }

    @Test
    public void greaterThanOrEqualFilterIsTranslatedToGe() {
        var filter = new GreaterThanOrEqualFilter(AttributeBuilder.build("age", 30));

        assertEquals(translator.translate(filter), "age ge 30");
    }

    @Test
    public void lessThanFilterIsTranslatedToLt() {
        var filter = new LessThanFilter(AttributeBuilder.build("age", 30));

        assertEquals(translator.translate(filter), "age lt 30");
    }

    @Test
    public void lessThanOrEqualFilterIsTranslatedToLe() {
        var filter = new LessThanOrEqualFilter(AttributeBuilder.build("age", 30));

        assertEquals(translator.translate(filter), "age le 30");
    }

    // ==================== value types ====================

    @Test
    public void booleanValueIsRenderedUnquoted() {
        var filter = new EqualsFilter(AttributeBuilder.build("active", true));

        assertEquals(translator.translate(filter), "active eq true");
    }

    @Test
    public void longValueIsRenderedAsNumber() {
        var filter = new EqualsFilter(AttributeBuilder.build("age", 12345678901L));

        assertEquals(translator.translate(filter), "age eq 12345678901");
    }

    @Test
    public void doubleValueIsRenderedAsNumber() {
        var filter = new EqualsFilter(AttributeBuilder.build("age", 1.5d));

        assertEquals(translator.translate(filter), "age eq 1.5");
    }

    @Test
    public void floatValueIsRenderedAsNumber() {
        var filter = new LessThanOrEqualFilter(AttributeBuilder.build("age", 2.5f));

        assertEquals(translator.translate(filter), "age le 2.5");
    }

    @Test
    public void guardedStringValueIsRenderedAsString() {
        var value = new GuardedString("secret".toCharArray());
        var filter = new EqualsFilter(AttributeBuilder.build("userName", value));

        assertEquals(translator.translate(filter), "userName eq \"secret\"");
    }

    @Test
    public void stringQuotesAreEscaped() {
        var filter = new EqualsFilter(AttributeBuilder.build("userName", "Jo\"hn"));

        assertEquals(translator.translate(filter), "userName eq \"Jo\\\"hn\"");
    }

    // ==================== attribute name mapping ====================

    @Test
    public void uidAttributeIsMappedToId() {
        var filter = new EqualsFilter(AttributeBuilder.build("uid", "1"));

        assertEquals(translator.translate(filter), "id eq \"1\"");
    }

    @Test
    public void nameAttributeIsMappedToUserName() {
        var filter = new EqualsFilter(AttributeBuilder.build("name", "jdoe"));

        assertEquals(translator.translate(filter), "userName eq \"jdoe\"");
    }

    @Test
    public void nestedAttributePathIsRenderedWithDots() {
        var filter = new EqualsFilter(AttributeBuilder.build("givenName", "Jane"));

        assertEquals(translator.translate(filter), "name.givenName eq \"Jane\"");
    }

    @Test
    public void extensionAttributePathIsRenderedWithSchemaUri() {
        var filter = new EqualsFilter(AttributeBuilder.build("employeeNumber", "12345"));

        assertEquals(translator.translate(filter),
                ENTERPRISE_USER_URI + ":employeeNumber eq \"12345\"");
    }

    // ==================== composite filters ====================

    @Test
    public void andFilterWithTwoOperandsIsTranslated() {
        var filter = new AndFilter(
                new EqualsFilter(AttributeBuilder.build("userName", "jdoe")),
                new EqualsFilter(AttributeBuilder.build("active", true)));

        assertEquals(translator.translate(filter), "(userName eq \"jdoe\" and active eq true)");
    }

    @Test
    public void andFilterWithThreeOperandsIsTranslated() {
        var filter = new AndFilter(List.of(
                new EqualsFilter(AttributeBuilder.build("userName", "jdoe")),
                new ContainsFilter(AttributeBuilder.build("userName", "do")),
                new EqualsFilter(AttributeBuilder.build("active", true))));

        assertEquals(translator.translate(filter),
                "(userName eq \"jdoe\" and userName co \"do\" and active eq true)");
    }

    @Test
    public void orFilterIsTranslated() {
        var filter = new OrFilter(
                new EqualsFilter(AttributeBuilder.build("userName", "jdoe")),
                new EqualsFilter(AttributeBuilder.build("userName", "asmith")));

        assertEquals(translator.translate(filter), "(userName eq \"jdoe\" or userName eq \"asmith\")");
    }

    @Test
    public void notFilterIsTranslated() {
        var filter = new NotFilter(new EqualsFilter(AttributeBuilder.build("userName", "jdoe")));

        assertEquals(translator.translate(filter), "not (userName eq \"jdoe\")");
    }

    @Test
    public void nestedCompositeFiltersAreTranslated() {
        var filter = new AndFilter(
                new NotFilter(new ContainsFilter(AttributeBuilder.build("userName", "do"))),
                new EqualsFilter(AttributeBuilder.build("active", true)));

        assertEquals(translator.translate(filter), "(not (userName co \"do\") and active eq true)");
    }

    @Test
    public void translationRoundTripsThroughScimFilterParser() throws Exception {
        var filters = List.<Filter>of(
                new EqualsFilter(AttributeBuilder.build("userName", "Jo\"hn \\ back")),
                new AndFilter(
                        new EqualsFilter(AttributeBuilder.build("userName", "jdoe")),
                        new OrFilter(
                                new EqualsFilter(AttributeBuilder.build("active", true)),
                                new GreaterThanFilter(AttributeBuilder.build("age", 30)))));
        for (var filter : filters) {
            var expression = translator.translate(filter);
            var reparsed = com.unboundid.scim2.common.filters.Filter.fromString(expression);
            assertEquals(reparsed.toString(), expression);
        }
    }

    // ==================== unsupported filters ====================

    @Test
    public void equalsIgnoreCaseFilterIsUnsupported() {
        var filter = new EqualsIgnoreCaseFilter(AttributeBuilder.build("userName", "JDOE"));

        assertThrows(IllegalArgumentException.class, () -> translator.translate(filter));
        assertFalse(translator.isTranslatable(filter));
    }

    @Test
    public void containsAllValuesFilterIsUnsupported() {
        var filter = new ContainsAllValuesFilter(AttributeBuilder.build("age", List.of(1, 2)));

        assertThrows(IllegalArgumentException.class, () -> translator.translate(filter));
        assertFalse(translator.isTranslatable(filter));
    }

    @Test
    public void externallyChainedFilterIsUnsupported() {
        var filter = new ExternallyChainedFilter(new EqualsFilter(AttributeBuilder.build("userName", "jdoe"))) {
            @Override
            public boolean accept(BaseObject object) {
                return false;
            }

            @Override
            public <R, P> R accept(FilterVisitor<R, P> visitor, P param) {
                return visitor.visitExtendedFilter(param, this);
            }
        };

        assertThrows(IllegalArgumentException.class, () -> translator.translate(filter));
        assertFalse(translator.isTranslatable(filter));
    }

    @Test
    public void unknownAttributeIsUnsupported() {
        var filter = new EqualsFilter(AttributeBuilder.build("unknownAttribute", "jdoe"));

        var exception = Assert.expectThrows(IllegalArgumentException.class, () -> translator.translate(filter));
        assertTrue(exception.getMessage().contains("unknownAttribute"));
        assertFalse(translator.isTranslatable(filter));
    }

    @Test
    public void valuePathAttributeIsUnsupported() {
        var filter = new EqualsFilter(AttributeBuilder.build("workEmail", "jdoe@example.com"));

        assertThrows(IllegalArgumentException.class, () -> translator.translate(filter));
        assertFalse(translator.isTranslatable(filter));
    }

    @Test
    public void nonStringValueOnStringFilterIsRejectedByFramework() {
        assertThrows(IllegalArgumentException.class,
                () -> new ContainsFilter(AttributeBuilder.build("active", true)));
    }

    @Test
    public void nonStringValueOnComparisonIsUnsupported() {
        var filter = new EqualsFilter(AttributeBuilder.build("active", new BigDecimal("1")));

        assertThrows(IllegalArgumentException.class, () -> translator.translate(filter));
        assertFalse(translator.isTranslatable(filter));
    }

    @Test
    public void unsupportedValueTypeIsUnsupported() {
        var filter = new EqualsFilter(AttributeBuilder.build("age", new BigDecimal("1.5")));

        assertThrows(IllegalArgumentException.class, () -> translator.translate(filter));
        assertFalse(translator.isTranslatable(filter));
    }

    @Test
    public void singleValueFilterWithoutValueIsUnsupported() {
        var filter = new EqualsFilter(AttributeBuilder.build("userName"));

        assertThrows(IllegalArgumentException.class, () -> translator.translate(filter));
        assertFalse(translator.isTranslatable(filter));
    }

    // ==================== isTranslatable / null ====================

    @Test
    public void nullFilterIsTranslatableAndTranslatesToNull() {
        assertTrue(translator.isTranslatable(null));
        assertNull(translator.translate(null));
    }

    @Test
    public void translatableFiltersAreRecognized() {
        assertTrue(translator.isTranslatable(new EqualsFilter(AttributeBuilder.build("userName", "jdoe"))));
        assertTrue(translator.isTranslatable(new AndFilter(
                new EqualsFilter(AttributeBuilder.build("userName", "jdoe")),
                new NotFilter(new ContainsFilter(AttributeBuilder.build("userName", "do"))))));
    }
}
