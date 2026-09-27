/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.impl.scim.flatten;

import com.evolveum.polygon.conndev.api.AttributePath;
import com.evolveum.polygon.scimrest.config.ScimClientConfiguration;
import com.unboundid.scim2.common.types.AttributeDefinition;
import com.unboundid.scim2.common.types.SchemaResource;
import org.identityconnectors.framework.common.exceptions.ConfigurationException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Builds the enabled {@link ComplexFlattenStrategy}s for a SCIM mapping configuration from the
 * per-family {@code SCIM Mapping} flags, and resolves the strategies effective for one object
 * class: the well-known families are enabled by the connector-level configuration or by the
 * object-class {@code scim { flatten ... }} list (additive — the list never disables what the
 * configuration enables), and any other listed attribute is flattened generically after being
 * validated against the resource's schema.
 */
public final class ScimFlattenStrategies {

    /** Entry types used when flattening multi-valued complex attributes by {@code type}. */
    public static final List<String> DEFAULT_TYPE_SET = List.of("work", "home", "other");

    /** The well-known flatten families (SCIM attribute names), in application order. */
    private static final List<String> KNOWN_FAMILIES = List.of("name", "emails", "phoneNumbers", "addresses");

    private static final String TYPE_DISCRIMINATOR = "type";

    private static final Set<String> ADDRESS_SUB_ATTRIBUTES =
            Set.of("formatted", "streetAddress", "locality", "region", "postalCode", "country");

    private ScimFlattenStrategies() {
    }

    /** The flatten strategies enabled by the given configuration, in application order. */
    public static List<ComplexFlattenStrategy> forConfiguration(ScimClientConfiguration configuration) {
        var strategies = new ArrayList<ComplexFlattenStrategy>();
        if (Boolean.TRUE.equals(configuration.getScimFlattenNameAttribute())) {
            strategies.add(new SingleValuedComplexFlattenStrategy("name"));
        }
        if (Boolean.TRUE.equals(configuration.getScimFlattenEmails())) {
            strategies.add(new TypeBasedComplexFlattenStrategy(
                    "emails", "email", DEFAULT_TYPE_SET, Set.of("value")));
        }
        if (Boolean.TRUE.equals(configuration.getScimFlattenPhoneNumbers())) {
            strategies.add(new TypeBasedComplexFlattenStrategy(
                    "phoneNumbers", "phone", DEFAULT_TYPE_SET, Set.of("value")));
        }
        if (Boolean.TRUE.equals(configuration.getScimFlattenAddresses())) {
            strategies.add(new TypeBasedComplexFlattenStrategy(
                    "addresses", "address", DEFAULT_TYPE_SET, ADDRESS_SUB_ATTRIBUTES));
        }
        return List.copyOf(strategies);
    }

    /**
     * The strategies effective for one object class:
     * <ul>
     *   <li>the well-known families are enabled when the corresponding connector-level
     *       {@code SCIM Mapping} property is {@code true} <em>or</em> the family is in
     *       {@code requestedFamilies} (additive — an object-class list extends the configured
     *       flattening, it never restricts it);</li>
     *   <li>any other requested attribute is flattened generically (single-valued:
     *       {@code <attr>_<sub>} per scalar sub-attribute; multi-valued with a scalar
     *       {@code type} sub-attribute: per entry type, like the well-known families) after
     *       being validated against {@code primarySchema}.</li>
     * </ul>
     *
     * @param globalConfiguration the connector-level SCIM mapping configuration
     * @param requestedFamilies the SCIM attribute names of the object-class {@code scim { flatten ... }} list
     * @param objectClassName the object class name, used in failure messages
     * @param primarySchema the resource's primary SCIM schema the requested attributes are validated against
     * @throws ConfigurationException when a requested attribute is missing from the schema, is
     *         not a complex attribute, or is a multi-valued attribute without a scalar
     *         {@code type} sub-attribute
     */
    public static List<ComplexFlattenStrategy> forObjectClass(ScimClientConfiguration globalConfiguration,
                                                              List<String> requestedFamilies,
                                                              String objectClassName,
                                                              SchemaResource primarySchema) {
        var requested = List.copyOf(requestedFamilies);
        var strategies = new ArrayList<ComplexFlattenStrategy>();
        if (Boolean.TRUE.equals(globalConfiguration.getScimFlattenNameAttribute()) || requested.contains("name")) {
            strategies.add(new SingleValuedComplexFlattenStrategy("name"));
        }
        if (Boolean.TRUE.equals(globalConfiguration.getScimFlattenEmails()) || requested.contains("emails")) {
            strategies.add(new TypeBasedComplexFlattenStrategy(
                    "emails", "email", DEFAULT_TYPE_SET, Set.of("value")));
        }
        if (Boolean.TRUE.equals(globalConfiguration.getScimFlattenPhoneNumbers()) || requested.contains("phoneNumbers")) {
            strategies.add(new TypeBasedComplexFlattenStrategy(
                    "phoneNumbers", "phone", DEFAULT_TYPE_SET, Set.of("value")));
        }
        if (Boolean.TRUE.equals(globalConfiguration.getScimFlattenAddresses()) || requested.contains("addresses")) {
            strategies.add(new TypeBasedComplexFlattenStrategy(
                    "addresses", "address", DEFAULT_TYPE_SET, ADDRESS_SUB_ATTRIBUTES));
        }
        for (var family : requested) {
            if (KNOWN_FAMILIES.contains(family)) {
                continue;
            }
            strategies.add(genericStrategy(family, objectClassName, null, primarySchema, List.of(), ""));
        }
        return List.copyOf(strategies);
    }

    /**
     * The strategies for the complex attributes listed in the flatten block of one declared SCIM
     * extension (the object-class {@code scim { extension(...) { flatten ... } }} configuration):
     * resolved generically against the extension's schema — the well-known families and the
     * connector-level {@code SCIM Mapping} flatten properties do not apply to extensions — with
     * the produced SCIM paths qualified with the extension schema URI and the flat attribute
     * names prefixed with the extension alias (e.g. {@code enterprise_work_photo}).
     *
     * @param objectClassName the object class name, used in failure messages
     * @param extensionAlias the alias the extension is declared under, used in flat names and failure messages
     * @param extensionSchema the SCIM schema of the extension the requested attributes are validated against
     * @param requestedFamilies the SCIM attribute names of the extension's flatten list
     * @throws ConfigurationException when a requested attribute is missing from the extension's
     *         schema, is not a complex attribute, or is a multi-valued attribute without a scalar
     *         {@code type} sub-attribute
     */
    public static List<ComplexFlattenStrategy> forExtensionSchema(String objectClassName,
                                                                  String extensionAlias,
                                                                  SchemaResource extensionSchema,
                                                                  List<String> requestedFamilies) {
        // A SCIM schema's identity is its id (the schema URI) — the same key the resource's
        // extension map and the path validation (ScimResourceContext) use.
        var prefix = List.<AttributePath.Component>of(new AttributePath.Extension(extensionSchema.getId()));
        var strategies = new ArrayList<ComplexFlattenStrategy>();
        for (var family : requestedFamilies) {
            strategies.add(genericStrategy(family, objectClassName, extensionAlias, extensionSchema, prefix, extensionAlias + "_"));
        }
        return List.copyOf(strategies);
    }

    /**
     * The generic strategy for a requested attribute validated against the given SCIM schema
     * (the resource's primary schema for {@link #forObjectClass}, the extension's schema for
     * {@link #forExtensionSchema}): the produced paths are prefixed with {@code pathPrefix}
     * (the extension schema URI for extensions) and the flat names with {@code namePrefix}
     * (the extension alias for extensions).
     */
    private static ComplexFlattenStrategy genericStrategy(String attribute,
                                                          String objectClassName,
                                                          String extensionAlias,
                                                          SchemaResource schema,
                                                          List<AttributePath.Component> pathPrefix,
                                                          String namePrefix) {
        var request = extensionAlias == null
                ? String.format("Object class '%s' requests flattening of '%s'", objectClassName, attribute)
                : String.format("Object class '%s' requests flattening of '%s' from extension '%s'",
                        objectClassName, attribute, extensionAlias);
        var attributeDefinition = findAttribute(schema, attribute);
        if (attributeDefinition == null) {
            throw new ConfigurationException(request
                    + ", but the SCIM schema of resource '" + schema.getName() + "' has no such attribute");
        }
        if (!AttributeDefinition.Type.COMPLEX.equals(attributeDefinition.getType())) {
            throw new ConfigurationException(request
                    + ", but it is not a complex attribute — only complex attributes can be flattened");
        }
        if (attributeDefinition.isMultiValued() && !hasTypeDiscriminator(attributeDefinition)) {
            throw new ConfigurationException(request + String.format(
                    ", but multi-valued complex attributes can only be flattened when they carry a scalar '%s' sub-attribute",
                    TYPE_DISCRIMINATOR));
        }
        if (attributeDefinition.isMultiValued()) {
            // every scalar sub-attribute except the discriminator (null inclusion set)
            return new TypeBasedComplexFlattenStrategy(attribute, singular(attribute), DEFAULT_TYPE_SET, null, pathPrefix, namePrefix);
        }
        return new SingleValuedComplexFlattenStrategy(attribute, pathPrefix, namePrefix);
    }

    private static AttributeDefinition findAttribute(SchemaResource schema, String name) {
        for (var attribute : schema.getAttributes()) {
            if (name.equals(attribute.getName())) {
                return attribute;
            }
        }
        return null;
    }

    private static boolean hasTypeDiscriminator(AttributeDefinition attributeDefinition) {
        var subAttributes = Objects.requireNonNullElse(attributeDefinition.getSubAttributes(),
                List.<AttributeDefinition>of());
        return subAttributes.stream().anyMatch(sub ->
                TYPE_DISCRIMINATOR.equals(sub.getName())
                        && !AttributeDefinition.Type.COMPLEX.equals(sub.getType()));
    }

    /**
     * The singular form of a generic multi-valued family's flat names
     * (e.g. {@code photos} → {@code work_photo}): drops one trailing {@code s}, or keeps the
     * name as-is. The well-known families use their own hardcoded singulars instead.
     */
    static String singular(String attribute) {
        return attribute.endsWith("s") ? attribute.substring(0, attribute.length() - 1) : attribute;
    }
}
