package com.evolveum.polygon.scim.rest.groovy;

import java.net.http.HttpResponse;

public interface TotalCountExtractor<T> {

    public static final TotalCountExtractor<?> UNSUPPORTED = (r) -> null;
    public static final TotalCountExtractor<?> SINGLE_OBJECT = (r) -> {
        if (r.statusCode() == 200) {
            return 1;
        } else {
            return 0;
        }
    };

    static <BF> TotalCountExtractor<BF> unsupported() {
        return (TotalCountExtractor<BF>) UNSUPPORTED;
    }

    static <BF> TotalCountExtractor<BF> singleObject() {
        return (TotalCountExtractor<BF>) SINGLE_OBJECT;
    }


    Integer extractTotalCount(HttpResponse<T> response);


}
