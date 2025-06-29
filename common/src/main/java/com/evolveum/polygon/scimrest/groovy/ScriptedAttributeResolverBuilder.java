/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.groovy;

import com.evolveum.polygon.scimrest.ContextLookup;
import com.evolveum.polygon.scimrest.groovy.api.AttributeResolutionContext;
import com.evolveum.polygon.scimrest.groovy.api.AttributeResolver;
import com.evolveum.polygon.scimrest.groovy.api.AttributeResolverBuilder;
import com.evolveum.polygon.scimrest.groovy.api.ObjectClassScripting;
import com.evolveum.polygon.scimrest.impl.rest.RestPagingAwareObjectRetriever;
import com.evolveum.polygon.scimrest.schema.MappedAttribute;
import com.evolveum.polygon.scimrest.schema.MappedObjectClass;
import com.evolveum.polygon.scimrest.spi.FilterAwareExecuteQueryProcessor;
import com.evolveum.polygon.scimrest.spi.SearchEndpointHandler;
import com.evolveum.polygon.scimrest.spi.TotalCountExtractor;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import org.identityconnectors.framework.common.objects.*;
import org.identityconnectors.framework.common.objects.filter.Filter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class ScriptedAttributeResolverBuilder implements AttributeResolverBuilder {

    private final Set<MappedAttribute> attributes = new HashSet<>();

    private final MappedObjectClass objectClass;
    private ResolutionType resolutionType = ResolutionType.PER_OBJECT;
    private Implementation implementation;

    public ScriptedAttributeResolverBuilder(ConnectorContext context, MappedObjectClass objectClass) {
        this.objectClass = objectClass;
    }

    @Override
    public ScriptedAttributeResolverBuilder attribute(String attributeName) {
        attributes.add(objectClass.attributeFromProtocolName(attributeName));
        return this;
    }

    @Override
    public ScriptedAttributeResolverBuilder resolutionType(ResolutionType type) {
        this.resolutionType = type;
        return this;
    }

    @Override
    public AttributeResolverBuilder search(@DelegatesTo(AttributeResolutionContext.class) Closure<Filter> closure) {
        // FIXME: rewrite that implementation will wrap logic already.
        this.implementation = new SearchBased(closure);
        return this;
    }

    @Override
    public ScriptedAttributeResolverBuilder implementation(@DelegatesTo(AttributeResolutionContext.class) Closure<?> closure) {
        this.implementation = new ClosureBased(closure);
        return this;
    }

    abstract class Implementation {

        abstract AttributeResolver build();
    }

    private class SearchBased extends Implementation {
        private final Closure<Filter> closure;

        public SearchBased(Closure<Filter> closure) {
            this.closure = closure;
        }

        @Override
        AttributeResolver build() {
            if (attributes.size() == 1) {
                var attribute = attributes.iterator().next();
                if (ConnectorObjectReference.class.equals(attribute.connId().getType())) {
                    var targetObjectClass = attribute.connId().getReferencedObjectClassName();
                    return new GroovySearchBasedReference(attribute, targetObjectClass, closure);
                }
            }
            throw new UnsupportedOperationException("Not supported yet.");
        }
    }

    private class ClosureBased extends Implementation {
        private final Closure<?> closure;

        public ClosureBased(Closure<?> closure) {
            this.closure = closure;
        }

        @Override
        AttributeResolver build() {
            return new GroovySingleResolver(objectClass.name(), Set.copyOf(attributes), closure);
        }
    }


    public AttributeResolver build() {
        if (implementation == null) {
            return null;
        }

        return implementation.build();


    }

    public ResolutionType resolutionType() {
        return resolutionType;
    }

    record GroovySearchBasedReference(MappedAttribute attribute,
                                              String targetObjectClass,
                                              Closure<Filter> implementation) implements AttributeResolver {

        @Override
        public Set<MappedAttribute> getSupportedAttributes() {
            return Set.of(attribute);
        }

        @Override
        public void resolveSingle(ContextLookup lookup, ConnectorObjectBuilder builder) {
            var context = lookup.get(ConnectorContext.class);
            var objectClass = context.schema().objectClass(targetObjectClass);
            var scriptContext = new SingleResolverContext(context, objectClass, builder);

            Filter filter = GroovyClosures.copyAndCall(implementation, scriptContext);
            var results = new ArrayList<ConnectorObject>();
            scriptContext.objectClass(targetObjectClass).search(filter, results::add, skipAttributeResolution());
            var attrValues = new ArrayList<ConnectorObjectReference>();
            for (var result : results) {
                attrValues.add(new ConnectorObjectReference(result));
            }
            builder.addAttribute(attribute.attributeOf(attrValues));
        }

        @Override
        public ResolutionType resolutionType() {
            return ResolutionType.PER_OBJECT;
        }
    }

    record GroovySingleResolver(String objectClass,
            Set<MappedAttribute> supportedAttributes,
    Closure<?> implementation) implements AttributeResolver {


        @Override
        public Set<MappedAttribute> getSupportedAttributes() {
            return supportedAttributes;
        }

        @Override
        public void resolveSingle(ContextLookup lookup, ConnectorObjectBuilder builder) {
            var context = lookup.get(ConnectorContext.class);
            var scriptContext = new SingleResolverContext(context, context.schema().objectClass(objectClass), builder);
            GroovyClosures.copyAndCall(implementation, scriptContext);
        }

        @Override
        public ResolutionType resolutionType() {
            return ResolutionType.PER_OBJECT;
        }
    }
    private record SingleResolverContext(ConnectorContext context, MappedObjectClass definition, ConnectorObjectBuilder value) implements AttributeResolutionContext {

        @Override
        public ObjectClassScripting objectClass(String name) {
            return ObjectClassScriptingFacade.from(context, name);
        }
    }

    public static class EndpointBasedSearchHandler<BF, OF> implements SearchEndpointHandler<BF, OF>, FilterAwareExecuteQueryProcessor {

        private final MappedObjectClass objectClass;
        private final ResponseObjectExtractor<BF,OF> objectExtractor;
        private final PagingHandler pagingSupport;
        private final String apiEndpoint;
        private final Set<FilterToRequestMapper> filterMappers;
        private final Class<?> responseFormat;
        private final TotalCountExtractor<BF> totalCountExtractor;

        public EndpointBasedSearchHandler(EndpointBasedSearchBuilder<BF, OF> builder, Set<FilterToRequestMapper> filterMappers) {
            this.objectClass = builder.objectClass;
            this.apiEndpoint = builder.path;
            this.objectExtractor = builder.objectExtractor;
            this.pagingSupport = builder.pagingSupport;
            this.filterMappers = new HashSet<>(filterMappers);
            this.responseFormat = builder.responseFormat;
            this.totalCountExtractor = builder.totalCountExtractor;
        }


        @Override
        public boolean supports(Filter filter) {
            return filterMappers.stream().anyMatch(m -> m.canHandle(filter));
        }

        @Override
        public RestSearchOperationHandler process(Filter filter) {
            var maybeMapper = filterMappers.stream().filter(m -> m.canHandle(filter)).findFirst();
            if  (maybeMapper.isEmpty()) {
                return null;
            }
            var mapper = maybeMapper.get();
            return RestSearchOperationHandler.<BF,OF>builder()
                    .addRequestUri((request, paging) -> {
                        request.apiEndpoint(apiEndpoint);
                        mapper.mapToRequest(request, filter);
                        if (pagingSupport != null) {
                            pagingSupport.handlePaging(request, paging);
                        }
                    })
                    .remoteObjectExtractor(objectExtractor::extractObjects)
                    .totalCountExtractor(this.totalCountExtractor)
                    .responseFormat(responseFormat)
                    .build();
        }

        @Override
        public void executeQuery(ContextLookup context, Filter filter, ResultsHandler resultsHandler, OperationOptions operationOptions) {
            // FIXME: naive refactor for now, reimplement to be better
            var spec = process(filter);
            if (spec != null) {
                new RestPagingAwareObjectRetriever(objectClass, spec).fetch(context, filter, resultsHandler, operationOptions);

            } else {
                throw new IllegalStateException("Cannot execute query");
            };
        }
    }
}
