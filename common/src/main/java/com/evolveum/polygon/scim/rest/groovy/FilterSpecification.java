package com.evolveum.polygon.scim.rest.groovy;

import org.identityconnectors.framework.common.objects.filter.AttributeFilter;
import org.identityconnectors.framework.common.objects.filter.ContainsFilter;
import org.identityconnectors.framework.common.objects.filter.EqualsFilter;
import org.identityconnectors.framework.common.objects.filter.Filter;

import java.util.function.Predicate;

@FunctionalInterface
public interface FilterSpecification {

    boolean matches(Filter filter);

    /**
     *
     * @param name ConnID attribute name
     * @return Filter specification for specific attribute name
     */
    static FilterSpecification.Attribute attribute(String name) {
        // FIXME: Here should be probably some translation between
        return filter -> name.equals(filter.getAttribute().getName());
    }


    static FilterSpecification.Attribute attribute(Predicate<AttributeFilter> filterPredicate) {
        return filter -> filterPredicate.test((AttributeFilter) filter);
    }

    @FunctionalInterface
    interface Attribute extends FilterSpecification {

        @Override
        default boolean matches(Filter filter) {
            if (filter instanceof AttributeFilter attrFilter) {
                return matches(attrFilter);
            }
            return false;
        }
        boolean matches(AttributeFilter filter);

        default FilterSpecification.Attribute eq() {
            return chain(f -> f instanceof EqualsFilter);
        }

        default FilterSpecification.Attribute contains() {
            return chain(f -> f instanceof ContainsFilter);
        }

        default Attribute chain(Attribute filterSpec) {
            return filter -> matches(filter) && filterSpec.matches(filter) ;
        }

        default Attribute anySingleValue() {
            return chain(filter -> filter.getAttribute().getValue().size() == 1);
        }
    }
}
