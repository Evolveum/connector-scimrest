package com.evolveum.polygon.scimrest.groovy;

import com.evolveum.polygon.scimrest.groovy.api.HttpMethod;
import org.identityconnectors.framework.common.objects.ConnectorObject;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Pattern;

public abstract class AbstractSingleObjectEndpointBuilder<I, O, E extends AbstractSingleObjectEndpointBuilder<I,O, E>> {

    private final static Pattern URI_PATH_PARAMETER_PATTERN = Pattern.compile("\\{([^}]+)}");

    protected final String path;
    protected HttpMethod httpMethod;

    protected final Map<String, PathParameter> pathParameters;
    protected final Map<String, AttributeSupport.Builder> supportedAttributes = new HashMap<>();


    public AbstractSingleObjectEndpointBuilder(String path) {
        this.path = path;
        this.pathParameters = pathParametersFrom(path);
    }

    public void httpOperation(HttpMethod method) {
        this.httpMethod = method;
    }



    public E supportedAttributes(String... attributes) {
        for (String attribute : attributes) {
            var attrBuilder = supportedAttribute(attribute);
            // Mark supported here
        }
        return self();
    }

    public AttributeSupport.Builder supportedAttribute(String attribute) {
        return supportedAttributes.computeIfAbsent(attribute, k -> new AttributeSupport.Builder());
    }


    public void pathParameter(String name, Function<ConnectorObject, Object> extractor) {

    }

    abstract protected E self();


    private static Map<String,PathParameter> pathParametersFrom(String path) {
        var ret = new HashMap<String,PathParameter>();
        var matcher = URI_PATH_PARAMETER_PATTERN.matcher(path);
        while (matcher.find()) {
            String name = matcher.group(1);
            ret.put(name, new PathParameter(name));
        }
        return Map.copyOf(ret);
    }

    public boolean matches(HttpMethod method, String path) {
        return Objects.equals(this.httpMethod, method) && Objects.equals(this.path, path);
    }

    static class PathParameter {

        private final String name;
        private Function<ConnectorObject, Object> extractor;

        public PathParameter(String name) {
            this.name = name;
        }
    }
}
