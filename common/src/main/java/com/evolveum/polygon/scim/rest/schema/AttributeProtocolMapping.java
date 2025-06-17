package com.evolveum.polygon.scim.rest.schema;

import java.util.List;

public interface AttributeProtocolMapping<O, A> {

    Class<?> connIdType();

    /**
     * Returns protocol representation of  attribute from protocol object.
     *
     * @return protocol representation of  attribute from protocol object. Null if attribute does not exists.
     */
    A attributeFromObject(O object);


    Object singleValueFromAttribute(A attribute);

    List<Object> valuesFromAttribute(A attribute);

    /**
     * Retrieves a single value from an protocol representation of object
     *
     * @param object The protocol object from which the attribute is retrieved.
     * @return A single value extracted from the attribute, or null if no such attribute exists.
     */
    default Object singleValueFromObject(O object) {
        var attr = attributeFromObject(object);
        if (attr != null) {
            return singleValueFromAttribute(attr);
        }
        return null;
    }

    /**
     * Returns a list of converted attribute values from protocol object
     *
     * @param object Protocol representation of object
     * @return null if attribute does not exits, otherwise list of converted values.
     */
    default List<Object> valuesFromObject(O object) {
        var attr = attributeFromObject(object);
        if (attr != null) {
            return valuesFromAttribute(attr);
        }
        return null;
    }


}
