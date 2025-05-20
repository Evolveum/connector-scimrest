package com.evolveum.polygon.scim.rest;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.ZonedDateTime;
import java.util.Base64;
import java.util.Set;

public enum OpenApiTypeMapping implements AttributeMapping {
    Base64Url("base64url","Binary data encoded as a url-safe string as defined in RFC4648", byte[].class, String.class) {
        @Override
        public Object toWireValue(Object value) throws IllegalArgumentException {
            if (value instanceof byte[] byteArrayVal) {
                return Base64.getUrlEncoder().encode(byteArrayVal);
            }
            return null;
        }

        @Override
        public Object toConnIdValue(Object value) throws IllegalArgumentException {
            if (value instanceof String stringVal) {
                return Base64.getUrlDecoder().decode(stringVal);
            }
            return null;
        }
    },
    Binary("binary","any sequence of octets", byte[].class, String.class ) {
        @Override
        public Object toWireValue(Object value) throws IllegalArgumentException {
            if (value instanceof String stringVal) {
                return Base64.getDecoder().decode(stringVal);
            }

            return null;
        }

        @Override
        public Object toConnIdValue(Object value) throws IllegalArgumentException {
            if (value instanceof byte[] byteArrayVal) {
                return Base64.getDecoder().decode(byteArrayVal);
            }

            return null;
        }
    },
    Byte("byte","base64 encoded data as defined in RFC4648", byte[].class, String.class) {
        @Override
        public Object toWireValue(Object value) throws IllegalArgumentException {
            if (value instanceof byte[] byteArrayVal) {
                return Base64.getDecoder().decode(byteArrayVal);
            }

            return null;
        }

        @Override
        public Object toConnIdValue(Object value) throws IllegalArgumentException {
            if (value instanceof String stringVal) {
                return Base64.getDecoder().decode(stringVal);
            }
            return null;
        }
    },
    Char("char","A single character", Character.class, String.class) {
        @Override
        public Object toWireValue(Object value) throws IllegalArgumentException {
            if (value instanceof String stringVal && stringVal.length() == 1) {
                return stringVal.charAt(0);
            }

            return null;
        }

        @Override
        public Object toConnIdValue(Object value) throws IllegalArgumentException {
            if (value instanceof Character characterVal) {
                return characterVal.toString();
            }

            return null;
        }
    },
    Commonmark("commonmark","commonmark-formatted text", String.class, String.class),
    DateTime("date-time","date and time as defined by date-time - RFC3339", ZonedDateTime.class, String.class) {
        @Override
        public Object toWireValue(Object value) throws IllegalArgumentException {
            if (value instanceof String stringVal) {
                return ZonedDateTime.parse(stringVal);
            }

            return null;
        }

        @Override
        public Object toConnIdValue(Object value) throws IllegalArgumentException {
            if (value instanceof ZonedDateTime zonedDateTimeVal) {
                return zonedDateTimeVal.toString();
            }

            return null;
        }
    },
    Date("date","date as defined by full-date - RFC3339", ZonedDateTime.class, String.class) {
        @Override
        public Object toWireValue(Object value) throws IllegalArgumentException {
            if (value instanceof String stringVal) {
                return ZonedDateTime.parse(stringVal);
            }

            return null;
        }

        @Override
        public Object toConnIdValue(Object value) throws IllegalArgumentException {
            if (value instanceof ZonedDateTime zonedDateTimeVal) {
                return zonedDateTimeVal.toString();
            }

            return null;
        }
    },
    Decimal("decimal","A fixed point decimal number of unspecified precision and range", BigDecimal.class, Number.class, String.class ) {
        @Override
        public Object toWireValue(Object value) throws IllegalArgumentException {
            if (value instanceof String stringVal) {
                if (stringVal.contains(".")) {
                    return new BigDecimal(stringVal);
                } else {
                    try {
                        return Integer.parseInt(stringVal);
                    } catch (NumberFormatException e) {
                        return new BigInteger(stringVal);
                    }
                }
            }

            return null;
        }

        @Override
        public Object toConnIdValue(Object value) throws IllegalArgumentException {
            if (value instanceof BigDecimal bigDecimalVal) {
                return bigDecimalVal.toString();
            } else if (value instanceof Number numberVal) {
                return numberVal.toString();
            }

            return null;
        }
    },
    Decimal128("decimal128","A decimal floating-point number with 34 significant decimal digits", BigDecimal.class, Number.class, String.class ) {
        @Override
        public Object toWireValue(Object value) throws IllegalArgumentException {
            if (value instanceof String stringVal) {
                if (stringVal.contains(".")) {
                    return new BigDecimal(stringVal);
                } else {
                    try {
                        return Integer.parseInt(stringVal);
                    } catch (NumberFormatException e) {
                        return new BigInteger(stringVal);
                    }
                }
            }

            return null;
        }

        @Override
        public Object toConnIdValue(Object value) throws IllegalArgumentException {
            if (value instanceof BigDecimal bigDecimalVal) {
                return bigDecimalVal.toString();
            } else if (value instanceof Number numberVal) {
                return numberVal.toString();
            }

            return null;
        }
    },
    DoubleInt("double-int","an integer that can be stored in an IEEE 754 double-precision number without loss of precision", BigInteger.class, Number.class ) {
        @Override
        public Object toWireValue(Object value) throws IllegalArgumentException {
            if (value instanceof Number numberVal) {
                return BigInteger.valueOf(numberVal.longValue());
            }

            return null;
        }

        @Override
        public Object toConnIdValue(Object value) throws IllegalArgumentException {
            if (value instanceof BigInteger bigIntegerVal) {
                return bigIntegerVal;
            }

            return null;
        }
    },
    Double("double","double precision floating point number", java.lang.Double.class, Number.class) {
        @Override
        public Object toWireValue(Object value) throws IllegalArgumentException {
            if (value instanceof Number numberVal) {
                return numberVal.doubleValue();
            }

            return null;
        }

        @Override
        public Object toConnIdValue(Object value) throws IllegalArgumentException {
            if (value instanceof Double doubleVal) {
                return doubleVal;
            }

            return null;
        }
    },
    Duration("duration","duration as defined by duration - RFC3339", String.class, String.class),
    Email("email","An email address as defined as Mailbox in RFC5321", String.class, String.class),
    Float("float","single precision floating point number", java.lang.Float.class, Number.class) {
        @Override
        public Object toWireValue(Object value) throws IllegalArgumentException {
            if (value instanceof Number numberVal) {
                return numberVal.floatValue();
            }

            return null;
        }

        @Override
        public Object toConnIdValue(Object value) throws IllegalArgumentException {
            if (value instanceof Float floatVal) {
                return floatVal.toString();
            }

            return null;
        }
    },
    Hostname("hostname","A host name as defined by RFC1123", String.class, String.class),
    Html("html","HTML-formatted text", String.class, String.class),
    HttpDate("http-date","date and time as defined by HTTP-date - RFC7231", String.class, String.class),
    IdnEmail("idn-email","An email address as defined as Mailbox in RFC6531", String.class, String.class),
    IdnHostname("idn-hostname","An internationalized host name as defined by RFC5890", String.class, String.class),
    Int16("int16","signed 16-bit integer", Integer.class, Number.class ) {
        @Override
        public Object toWireValue(Object value) throws IllegalArgumentException {
            if (value instanceof Number numberVal) {
                return numberVal.intValue();
            }

            return null;
        }

        @Override
        public Object toConnIdValue(Object value) throws IllegalArgumentException {
            if (value instanceof Integer integerVal) {
                return integerVal.toString();
            }

            return null;
        }
    },
    Int32("int32","signed 32-bit integer", Integer.class, Number.class ) {
        @Override
        public Object toWireValue(Object value) throws IllegalArgumentException {
            if (value instanceof Number numberVal) {
                return numberVal.intValue();
            }

            return null;
        }

        @Override
        public Object toConnIdValue(Object value) throws IllegalArgumentException {
            if (value instanceof Integer integerVal) {
                return integerVal.toString();
            }

            return null;
        }
    },
    Int64("int64","signed 64-bit integer", Long.class, String.class) {
        @Override
        public Object toWireValue(Object value) throws IllegalArgumentException {
            if (value instanceof String stringVal) {
                return Long.parseLong(stringVal);
            }

            return null;
        }

        @Override
        public Object toConnIdValue(Object value) throws IllegalArgumentException {
            if (value instanceof Long longVal) {
                return longVal.toString();
            }

            return null;
        }
    },
    Int8("int8","signed 8-bit integer", java.lang.Byte.class, Number.class) {
        @Override
        public Object toWireValue(Object value) throws IllegalArgumentException {
            if (value instanceof Number numberVal) {
                return numberVal.byteValue();
            }

            return null;
        }

        @Override
        public Object toConnIdValue(Object value) throws IllegalArgumentException {
            if (value instanceof Byte byteValue) {
                return byteValue.intValue();
            }

            return null;
        }
    },
    Ipv4("ipv4","An IPv4 address as defined as dotted-quad by RFC2673", String.class, String.class),
    Ipv6("ipv6","An IPv6 address as defined by RFC4673", String.class, String.class),
    IriReference("iri-reference","A Internationalized Resource Identifier as defined in RFC3987", String.class, String.class),
    Iri("iri","A Internationalized Resource Identifier as defined in RFC3987", String.class, String.class),
    JsonPointer("json-pointer","A JSON string representation of a JSON Pointer as defined in RFC6901", String.class, String.class),
    MediaRange("media-range","A media type as defined by the media-range ABNF production in RFC9110.", String.class, String.class),
    Password("password","a string that hints to obscure the value.", String.class, String.class),
    Regex("regex","A regular expression as defined in ECMA-262", String.class, String.class),
    RelativeJsonPointer("relative-json-pointer","A JSON string representation of a relative JSON Pointer as defined in draft RFC 01", String.class, String.class),
    SfBinary("sf-binary","structured fields byte sequence as defined in [RFC8941]", byte[].class, String.class) {
        @Override
        public Object toWireValue(Object value) throws IllegalArgumentException {
            return Base64.getDecoder().decode((String) value);
        }

        @Override
        public Object toConnIdValue(Object value) throws IllegalArgumentException {
            return Base64.getEncoder().encodeToString((byte[]) value);
        }
    },
    SfBoolean("sf-boolean","structured fields boolean as defined in [RFC8941]", Boolean.class, String.class) {
        @Override
        public Object toWireValue(Object value) throws IllegalArgumentException {
            if (value instanceof String stringVal) {
                return Boolean.parseBoolean(stringVal);
            }

            return null;
        }

        @Override
        public Object toConnIdValue(Object value) throws IllegalArgumentException {
            if (value instanceof Boolean boolVal) {
                return boolVal.toString();
            }

            return null;
        }
    },
    SfDecimal("sf-decimal","structured fields decimal as defined in [RFC8941]", BigDecimal.class, Number.class) {
        @Override
        public Object toWireValue(Object value) throws IllegalArgumentException {
            if (value instanceof String stringVal) {
                if (stringVal.contains(".")) {
                    return new BigDecimal(stringVal);
                } else {
                    try {
                        return Integer.parseInt(stringVal);
                    } catch (NumberFormatException e) {
                        return new BigInteger(stringVal);
                    }
                }
            }

            return null;
        }

        @Override
        public Object toConnIdValue(Object value) throws IllegalArgumentException {
            if (value instanceof BigDecimal bigDecimalVal) {
                return bigDecimalVal.toString();
            } else if (value instanceof Number numberVal) {
                return numberVal.toString();
            }

            return null;
        }
    },
    SfInteger("sf-integer","structured fields integer as defined in [RFC8941]", Integer.class, Number.class) {
        @Override
        public Object toWireValue(Object value) throws IllegalArgumentException {
            if (value instanceof Number numberVal) {
                return numberVal.intValue();
            }

            return null;
        }

        @Override
        public Object toConnIdValue(Object value) throws IllegalArgumentException {
            if (value instanceof Integer integerVal) {
                return integerVal.toString();
            }

            return null;
        }
    },
    SfString("sf-string","structured fields string as defined in [RFC8941]", String.class, String.class),
    SfToken("sf-token","structured fields token as defined in [RFC8941]", String.class, String.class),
    Time("time","time as defined by full-time - RFC3339", String.class, String.class),
    Uint8("uint8","unsigned 8-bit integer", Integer.class, Number.class) {
        @Override
        public Object toWireValue(Object value) throws IllegalArgumentException {
            if (value instanceof Number numberVal) {
                return numberVal.intValue();
            }

            return null;
        }

        @Override
        public Object toConnIdValue(Object value) throws IllegalArgumentException {
            if (value instanceof Integer integerVal) {
                return integerVal.toString();
            }

            return null;
        }
    },
    UriReference("uri-reference","A URI reference as defined in RFC3986", String.class, String.class),
    UriTemplate("uri-template","A URI Template as defined in RFC6570", String.class, String.class),
    Uri("uri","A Uniform Resource Identifier as defined in RFC3986", String.class, String.class),
    Uuid("uuid", "A Universally Unique Identifier as defined in RFC4122", String.class, String.class);

    private final String openApiFormat;
    private final Set<Class<?>> availableWireTypes;
    private final Class<?> connidClass;
    private final Class<?> primaryWireType;

    OpenApiTypeMapping(String openApiFormat, String description, Class<?> connidClass, Class<?>... jsonClass) {
        this.openApiFormat = openApiFormat;
        this.primaryWireType = jsonClass[0];
        this.availableWireTypes = Set.of(jsonClass);
        this.connidClass = connidClass;
    }



    public Set<Class<?>> getJsonClasses() {
        return availableWireTypes;
    }

    public Class<?> connIdClass() {
        return connidClass;
    }

    @Override
    public Class<?> connIdType() {
        return connidClass;
    }

    @Override
    public Class<?> primaryWireType() {
        return primaryWireType;
    }

    @Override
    public Set<Class<?>> supportedWireTypes() {
        return availableWireTypes;
    }

    @Override
    public Object toWireValue(Object value) throws IllegalArgumentException {
        // FIXME implement proper convertors
        return value;
    }

    @Override
    public Object toConnIdValue(Object value) throws IllegalArgumentException {
        // FIXME implement proper convertors
        return value;
    }

    public static AttributeMapping from(String jsonType, String openApiFormat) {
        for (OpenApiTypeMapping am : values()) {
            if (am.openApiFormat.equals(openApiFormat)) {
                return am;
            }
        };
        return PureJsonMapping.from(jsonType);
    }
}
