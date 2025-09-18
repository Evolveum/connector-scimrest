/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *  
 */

package com.evolveum.polygon.scimrest.groovy;

import com.evolveum.polygon.scimrest.ContextLookup;
import com.evolveum.polygon.scimrest.api.AttributePath;
import com.evolveum.polygon.scimrest.groovy.api.FilterSpecification;
import com.evolveum.polygon.scimrest.spi.ExecuteQueryProcessor;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.*;
import org.identityconnectors.framework.common.objects.filter.*;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.HashSet;
import java.util.function.BiFunction;
import java.util.function.UnaryOperator;

public class NormalizationQueryProcessor implements ExecuteQueryProcessor {

    private final String propertyToNormalize;
    private final ExecuteQueryProcessor delegate;
    private final BiFunction<String, Object, String> nameTransformer;
    private final BiFunction<String, Object, String> uidTransformer;
    private final UnaryOperator<String> nameRestorer;
    private final UnaryOperator<String> uidRestorer;

    public NormalizationQueryProcessor(String propertyToNormalize, ExecuteQueryProcessor executeQueryProcessor, BiFunction<String, Object, String> nameTransformer, BiFunction<String, Object, String> uidTransformer) {

        this(propertyToNormalize, executeQueryProcessor, nameTransformer, uidTransformer, null, null);
    }

    public NormalizationQueryProcessor(String propertyToNormalize, ExecuteQueryProcessor executeQueryProcessor,
                                       BiFunction<String, Object, String> nameTransformer,
                                       BiFunction<String, Object, String> uidTransformer,
                                       UnaryOperator<String> nameRestorer,
                                       UnaryOperator<String> uidRestorer
    ) {
        this.propertyToNormalize = propertyToNormalize;
        this.delegate = executeQueryProcessor;
        this.nameTransformer = nameTransformer;
        this.uidTransformer = uidTransformer;
        this.nameRestorer = nameRestorer;
        this.uidRestorer = uidRestorer;
    }

    @Override
    public void executeQuery(ContextLookup context, Filter filter, ResultsHandler resultsHandler, OperationOptions operationOptions) {
        // FIXME: We should rewrite filter here


        var processedFilter = reverseFilterValue(filter);
        delegate.executeQuery(context, processedFilter, new TransformationHandler(resultsHandler, filter), operationOptions);
    }

    private Filter reverseFilterValue(Filter filter) {
        if(filter==null) {
            return filter;
        }
        Class<Filter> filterClass = (Class<Filter>) filter.getClass();

        if (filter instanceof AttributeFilter) {
            var tmpFilter = (AttributeFilter) filter;
            var attr = tmpFilter.getAttribute().getValue();
            var attrName = tmpFilter.getName();

            if (Name.NAME.equals(attrName)) {

                return createReversedFiler(filterClass, AttributeBuilder.build(attrName,
                        restoreName(attr.get(0).toString()).getValue()));
            } else if (Uid.NAME.equals(attrName)) {

                return createReversedFiler(filterClass, AttributeBuilder.build(attrName,
                       restoreUid(attr.get(0).toString()).getValue()));
            }
        }
        return filter;
    }

    private class TransformationHandler implements ResultsHandler {
        private final ResultsHandler delegate;
        private final Filter filter;

        public TransformationHandler(ResultsHandler resultsHandler, Filter filter) {
            this.delegate = resultsHandler;

           if( FilterSpecification.attribute(Uid.NAME).eq().anySingleValue().matches(filter)){
               this.filter = filter;
           } else {
               this.filter = null;
           }

        }

        @Override
        public boolean handle(ConnectorObject original) {
            var common = new HashSet<>(original.getAttributes());

            var uid = original.getUid();
            var name = original.getName();
            var toNormalize = original.getAttributeByName(propertyToNormalize);
            // FIXME: What should happen if normalization attribute is not present?

            common.remove(uid);
            common.remove(name);
            common.remove(toNormalize);

            for (var value : toNormalize.getValue()) {
                var newName = computeName(name.getNameValue(), value);
                var newUid = computeUid(uid.getUidValue(), value);
                var newAttr = AttributeBuilder.build(toNormalize.getName(), value);
                var newAttributes = new HashSet<>(common);
                newAttributes.add(newUid);
                newAttributes.add(newName);
                newAttributes.add(newAttr);

                var newObj = new ConnectorObject(original.getObjectClass(), newAttributes);
                if (matchesFilter(newObj)) {
                    if (!delegate.handle(newObj)) {
                        return false;
                    }
                }
            }
            return true;
        }

        private boolean matchesFilter(ConnectorObject newObj) {
            if (filter == null) {
                return true;
            }
            return filter.accept(newObj);
        }
    }

    Name computeName(String original, Object value) {
        return new Name(nameTransformer.apply(original, value));
    }

    Uid computeUid(String original, Object value) {
        return new Uid(uidTransformer.apply(original, value));
    }

    Name restoreName(String value) {

        return new Name(nameRestorer.apply(value));
    }

    Uid restoreUid(String value) {
        return new Uid(uidRestorer.apply(value));
    }

    Filter createReversedFiler(Class<Filter> clazz, Attribute attr) {

        for (Constructor<?> ctor : clazz.getConstructors()) {
            Class<?>[] params = ctor.getParameterTypes();
            if (params.length == 1) {
                Class<?> paramType = params[0];
                if (paramType.isAssignableFrom(attr.getClass())) {
                    try {
                        return clazz.cast(ctor.newInstance(attr));
                    } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
                        throw new ConnectorException("Exception during the construction of query filter: "+ e);
                    }
                }
            }
        }
        throw new ConnectorException("Exception during the construction of query filter, " +
                "constructor for filter class not found");
    }
}
