package com.evolveum.polygon.scim.rest.groovy.spi;

import java.net.http.HttpResponse;

public record ResponseWrapper<BF>(HttpResponse<BF> response) {
}
