package com.evolveum.polygon.scim.rest.groovy;

import org.json.JSONObject;

import java.net.http.HttpResponse;
import java.util.function.Function;

@FunctionalInterface
public interface ResponseObjectExtractor<BF, OF> {

    Iterable<OF> extractObjects( HttpResponse<BF> response);

}
