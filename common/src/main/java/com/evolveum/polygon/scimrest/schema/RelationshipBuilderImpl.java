/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.schema;

import com.evolveum.polygon.conndev.concepts.GroovyClosures;
import com.evolveum.polygon.conndev.concepts.SourceLocation;
import com.evolveum.polygon.conndev.schema.AbstractRelationshipBuilder;
import com.evolveum.polygon.scimrest.groovy.api.RestRelationshipBuilder;
import groovy.lang.Closure;
import org.identityconnectors.framework.common.exceptions.ConfigurationException;
import org.identityconnectors.framework.common.objects.AttributeInfo;

public class RelationshipBuilderImpl implements RestRelationshipBuilder, GroovyClosures.ClosureExecutionAware {

    private final RestSchemaBuilderImpl schemaBuilder;

    final String name;
    ParticipantBuilder subject;
    ParticipantBuilder object;

    public RelationshipBuilderImpl(String name, RestSchemaBuilderImpl schemaBuilder) {
        this.name = name;
        this.schemaBuilder = schemaBuilder;
    }

    private ParticipantBuilder participant(String objectClass) {
        // Capture the script location of the participant declaration for configuration errors.
        return new ParticipantBuilder(this, schemaBuilder.objectClass(objectClass), SourceLocation.capture());
    }

    @Override
    public Participant subject(String objectClass, Closure<?> closure) {
        if (subject == null) {
            subject = participant(objectClass);
        }
        return GroovyClosures.callAndReturnDelegate(closure,subject);
    }

    @Override
    public Participant object(String objectClass, Closure<?> closure) {
        if (object == null) {
            object = participant(objectClass);
        }
        return GroovyClosures.callAndReturnDelegate(closure,object);
    }

    /**
     * Runs after the relationship closure has been executed.
     *
     * <p>Configures the side mappings, failing with a {@link ConfigurationException} when a
     * participant declares no reference attribute (instead of surfacing a
     * {@code NullPointerException}).</p>
     *
     * @throws ConfigurationException if either participant declares no reference attribute
     */
    @Override
    public void afterExecution() {
        if (subject != null && object != null) {
            // configure side mappings.
            var subjectAttribute = requireParticipantAttribute(subject, "subject");
            var objectAttribute = requireParticipantAttribute(object, "object");

            subjectAttribute.objectClass(object.objectClass().getObjectClassValue());
            subjectAttribute.role(AttributeInfo.RoleInReference.SUBJECT);

            objectAttribute.objectClass(subject.objectClass().getObjectClassValue());
            objectAttribute.role(AttributeInfo.RoleInReference.OBJECT);

        }
    }

    /**
     * Returns the participant's reference attribute, failing fast when it was not declared.
     *
     * @param participant the participant to check
     * @param side the participant's role within the relationship ({@code "subject"} or {@code "object"})
     * @return the participant's reference attribute
     * @throws ConfigurationException if the participant declares no reference attribute
     */
    private ParticipantBuilder.AttributeBuilder requireParticipantAttribute(ParticipantBuilder participant, String side) {
        var attribute = participant.attribute();
        if (attribute == null) {
            throw new ConfigurationException(AbstractRelationshipBuilder.missingAttributeMessage(
                    name, side, participant.objectClass().getObjectClassValue(), participant.location()));
        }
        return attribute;
    }
}
