package com.evolveum.polygon.scim.rest.schema;

import com.evolveum.polygon.scim.rest.groovy.GroovyClosures;
import com.evolveum.polygon.scim.rest.groovy.api.RestRelationshipBuilder;
import groovy.lang.Closure;

public class RelationshipBuilderImpl implements RestRelationshipBuilder, GroovyClosures.ClosureExecutionAware {

    private final RestSchemaBuilder schemaBuilder;

    final String name;
    ParticipantBuilder subject;
    ParticipantBuilder object;

    public RelationshipBuilderImpl(String name, RestSchemaBuilder schemaBuilder) {
        this.name = name;
        this.schemaBuilder = schemaBuilder;
    }

    private ParticipantBuilder participant(String objectClass) {
        return new ParticipantBuilder(this, schemaBuilder.objectClass(objectClass));
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

    @Override
    public void afterExecution() {
        if (subject != null && object != null) {
            // configure side mappings.
            subject.attribute().objectClass(object.objectClass());
            object.attribute().objectClass(subject.objectClass());


        }
    }
}
