package com.evolveum.polygon.scim.rest.groovy;

import org.identityconnectors.framework.common.objects.Attribute;

public interface AttributeSpecification {

    boolean matches(Attribute value);

}
