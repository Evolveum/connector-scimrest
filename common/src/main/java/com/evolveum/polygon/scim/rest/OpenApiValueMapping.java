/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scim.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Set;

import static com.evolveum.polygon.scim.rest.JsonSchemaValueMapping.*;

/**
 * Provides a mapping between OpenAPI data formats, their corresponding JSON
 * representations, wire types, and ConnId class types.
 * This class enables conversion of values between OpenAPI-compliant wire
 * types and ConnId-compliant native types, while also defining the primarySchema
 * and supported wire types.
 *
 */
public enum OpenApiValueMapping implements JsonValueMapping {
    Base64Url("base64url","Binary data encoded as a url-safe string as defined in RFC4648", byte[].class, STRING) {
        @Override
        public JsonNode toWireValue(Object value) throws IllegalArgumentException {
            if (value instanceof byte[] byteArrayVal) {
                return JsonNodeFactory.instance.textNode(Base64.getUrlEncoder().encodeToString(byteArrayVal));
            }
            throw new IllegalArgumentException("Cannot convert " + value.getClass() + " to " + this.getClass().getSimpleName());
        }

        @Override
        public Object toConnIdValue(JsonNode value) throws IllegalArgumentException {
            if (value instanceof BinaryNode binaryVal) {
                return binaryVal.binaryValue();
            }
            if (value instanceof TextNode stringVal) {
                return Base64.getUrlDecoder().decode(stringVal.asText());
            }
            throw cannotConvertToConnId(value);
        }
    },
    Binary("binary","any sequence of octets", byte[].class, BINARY),
    Byte("byte","base64 encoded data as defined in RFC4648", byte[].class, STRING) {
        @Override
        public JsonNode toWireValue(Object value) throws IllegalArgumentException {
            if (value instanceof byte[] byteArrayVal) {
                return JsonNodeFactory.instance.textNode(Base64.getEncoder().encodeToString(byteArrayVal));
            }
            throw new IllegalArgumentException("Cannot convert " + value.getClass() + " to " + this.getClass().getSimpleName());
        }

        @Override
        public Object toConnIdValue(JsonNode value) throws IllegalArgumentException {
            var ret = baseMapping.toConnIdValue(value);
            if (ret instanceof String stringVal) {
                return Base64.getDecoder().decode(stringVal);
            }
            return null;
        }
    },
    Char("char","A single character", Character.class, STRING) {
        @Override
        public JsonNode toWireValue(Object value) throws IllegalArgumentException {
            if (value instanceof String stringVal && stringVal.length() == 1) {
                return JsonNodeFactory.instance.textNode(stringVal.substring(0, 1));
            }

            return null;
        }

        @Override
        public Object toConnIdValue(JsonNode value) throws IllegalArgumentException {
            if (value instanceof TextNode characterVal) {
                return characterVal.toString();
            }

            return null;
        }
    },
    Commonmark("commonmark","commonmark-formatted text", String.class,STRING),
    DateTime("date-time","date and time as defined by date-time - RFC3339", ZonedDateTime.class, STRING) {
        @Override
        public JsonNode toWireValue(Object value) throws IllegalArgumentException {
            if (value instanceof ZonedDateTime zonedDateTimeVal) {
                return JsonNodeFactory.instance.textNode(DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(zonedDateTimeVal.toInstant()));
            }
            throw new IllegalArgumentException("Cannot convert " + value.getClass() + " to " + this.getClass().getSimpleName());
        }

        @Override
        public Object toConnIdValue(JsonNode value) throws IllegalArgumentException {
            if (value instanceof TextNode stringVal) {
                return ZonedDateTime.parse(stringVal.asText());
            }
            return null;
        }
    },
    Date("date","date as defined by full-date - RFC3339", ZonedDateTime.class, STRING) {
        @Override
        public JsonNode toWireValue(Object value) throws IllegalArgumentException {
            // FIXME: Implement later
            throw new IllegalArgumentException("Cannot convert " + value.getClass() + " to " + this.getClass().getSimpleName());

        }

        @Override
        public Object toConnIdValue(JsonNode value) throws IllegalArgumentException {
            if (value instanceof TextNode stringVal) {
                return ZonedDateTime.parse(stringVal.asText());
            }
            throw new IllegalArgumentException("Cannot convert " + value.getClass() + " to " + this.getClass().getSimpleName());
        }
    },
    Decimal("decimal","A fixed point decimal number of unspecified precision and range", BigDecimal.class, NUMBER, DecimalNode.class) {
        @Override
        public JsonNode toWireValue(Object value) throws IllegalArgumentException {
            if (value instanceof BigDecimal bigDecimalVal) {
                return new DecimalNode(bigDecimalVal);
            } else if (value instanceof Number numberVal) {
                return new DecimalNode(BigDecimal.valueOf(numberVal.doubleValue()));
            }
            return null;
        }

        @Override
        public Object toConnIdValue(JsonNode value) throws IllegalArgumentException {
            if (value instanceof NumericNode node) {
                return node.decimalValue();
            }
            throw cannotConvertToConnId(value);
        }
    },
    Decimal128("decimal128","A decimal floating-point number with 34 significant decimal digits", BigDecimal.class, NUMBER ) {
        @Override
        public JsonNode toWireValue(Object value) throws IllegalArgumentException {
            if (value instanceof BigDecimal bigDecimalVal) {
                return new DecimalNode(bigDecimalVal);
            } else if (value instanceof Number numberVal) {
                return new DecimalNode(BigDecimal.valueOf(numberVal.doubleValue()));
            }
            throw cannotConvertToWire(value);
        }

        @Override
        public Object toConnIdValue(JsonNode value) throws IllegalArgumentException {
            if (value instanceof NumericNode node) {
                return node.decimalValue();
            }
            throw cannotConvertToConnId(value);
        }
    },
    DoubleInt("double-int","an integer that can be stored in an IEEE 754 double-precision number without loss of precision", BigInteger.class, NUMBER ) {
        @Override
        public JsonNode toWireValue(Object value) throws IllegalArgumentException {
            if (value instanceof BigInteger bigIntegerVal) {
                return new BigIntegerNode(bigIntegerVal);
            }
            throw new IllegalArgumentException("Cannot convert " + value.getClass() + " to " + this.getClass().getSimpleName());
        }

        @Override
        public Object toConnIdValue(JsonNode value) throws IllegalArgumentException {
            if (value instanceof NumericNode numberVal) {
                return numberVal.bigIntegerValue();
            }
            throw new IllegalArgumentException("Cannot convert " + value.getClass() + " to " + this.getClass().getSimpleName());
        }
    },
    Double("double","double precision floating point number", java.lang.Double.class, NUMBER, DoubleNode.class) {
        @Override
        public JsonNode toWireValue(Object value) throws IllegalArgumentException {
            if (value instanceof Double numberVal) {
                return new DoubleNode(numberVal);
            }
            throw cannotConvertToWire(value);
        }

        @Override
        public Object toConnIdValue(JsonNode value) throws IllegalArgumentException {
            if (value instanceof NumericNode doubleVal) {
                return doubleVal.doubleValue();
            }
            throw cannotConvertToConnId(value);
        }
    },
    Duration("duration","duration as defined by duration - RFC3339", String.class,STRING),
    Email("email","An email address as defined as Mailbox in RFC5321", String.class,STRING),
    Float("float","single precision floating point number", java.lang.Float.class, NUMBER) {
        @Override
        public JsonNode toWireValue(Object value) throws IllegalArgumentException {
            if (value instanceof Float numberVal) {
                return new FloatNode(numberVal);
            }
            throw cannotConvertToWire(value);
        }

        @Override
        public Object toConnIdValue(JsonNode value) throws IllegalArgumentException {
            if (value instanceof NumericNode floatVal) {
                return floatVal.floatValue();
            }
            return cannotConvertToConnId(value);
        }
    },
    Hostname("hostname","A host name as defined by RFC1123", String.class,STRING),
    Html("html","HTML-formatted text", String.class,STRING),
    HttpDate("http-date","date and time as defined by HTTP-date - RFC7231", String.class,STRING),
    IdnEmail("idn-email","An email address as defined as Mailbox in RFC6531", String.class,STRING),
    IdnHostname("idn-hostname","An internationalized host name as defined by RFC5890", String.class,STRING),
    Int16("int16","signed 16-bit integer", Integer.class, INTEGER, ShortNode.class ) {
        @Override
        public JsonNode toWireValue(Object value) throws IllegalArgumentException {
            if (value instanceof Integer integerVal) {
                value = integerVal.shortValue();
            }
            if (value instanceof Short numberVal) {
                return new ShortNode(numberVal);
            }
            throw cannotConvertToWire(value);
        }

        @Override
        public Object toConnIdValue(JsonNode value) throws IllegalArgumentException {
            if (value instanceof NumericNode integerVal) {
                return (int) integerVal.shortValue();
            }
            return cannotConvertToConnId(value);
        }
    },
    Int32("int32","signed 32-bit integer", Integer.class, INTEGER),
    Int64("int64","signed 64-bit integer", Long.class, NUMBER, LongNode.class) {
        @Override
        public JsonNode toWireValue(Object value) throws IllegalArgumentException {
            if (value instanceof Long longVal) {
                return new LongNode(longVal.longValue());
            }
            throw new UnsupportedOperationException();
        }

        @Override
        public Object toConnIdValue(JsonNode value) throws IllegalArgumentException {
            if (value instanceof LongNode longVal) {
                return longVal.longValue();
            }
            if (value instanceof NumericNode numericVal) {
                return numericVal.longValue();
            }
            throw new UnsupportedOperationException();
        }
    },
    Int8("int8","signed 8-bit integer", java.lang.Byte.class, INTEGER) {
        @Override
        public JsonNode toWireValue(Object value) throws IllegalArgumentException {
            if (value instanceof Number numberVal) {
                return new IntNode(numberVal.byteValue());
            }

            return null;
        }

        @Override
        public Object toConnIdValue(JsonNode value) throws IllegalArgumentException {
            var ret = baseMapping.toConnIdValue(value);
            if (ret instanceof Number val) {
                return val.byteValue();
            }
            return null;
        }
    },
    Ipv4("ipv4","An IPv4 address as defined as dotted-quad by RFC2673", String.class,STRING),
    Ipv6("ipv6","An IPv6 address as defined by RFC4673", String.class,STRING),
    IriReference("iri-reference","A Internationalized Resource Identifier as defined in RFC3987", String.class,STRING),
    Iri("iri","A Internationalized Resource Identifier as defined in RFC3987", String.class,STRING),
    JsonPointer("json-pointer","A JSON string representation of a JSON Pointer as defined in RFC6901", String.class,STRING),
    MediaRange("media-range","A media type as defined by the media-range ABNF production in RFC9110.", String.class,STRING),
    Password("password","a string that hints to obscure the value.", String.class,STRING),
    Regex("regex","A regular expression as defined in ECMA-262", String.class,STRING),
    RelativeJsonPointer("relative-json-pointer","A JSON string representation of a relative JSON Pointer as defined in draft RFC 01", String.class,STRING),
    SfBinary("sf-binary","structured fields byte sequence as defined in [RFC8941]", byte[].class, BINARY),
    SfBoolean("sf-boolean","structured fields boolean as defined in [RFC8941]", Boolean.class, BOOLEAN),
    SfDecimal("sf-decimal","structured fields decimal as defined in [RFC8941]", BigDecimal.class, NUMBER) {
        @Override
        public JsonNode toWireValue(Object value) throws IllegalArgumentException {
            if (value instanceof BigDecimal stringVal) {
                return new DecimalNode(stringVal);
            }
            throw cannotConvertToWire(value);
        }

        @Override
        public Object toConnIdValue(JsonNode value) throws IllegalArgumentException {
            if (value instanceof DecimalNode bigDecimalVal) {
                return bigDecimalVal.decimalValue();
            }
            if (value instanceof NumericNode numericVal) {
                return numericVal.decimalValue();
            }
            throw cannotConvertToConnId(value);
        }
    },
    SfInteger("sf-integer","structured fields integer as defined in [RFC8941]", Integer.class, INTEGER),
    SfString("sf-string","structured fields string as defined in [RFC8941]", String.class,STRING),
    SfToken("sf-token","structured fields token as defined in [RFC8941]", String.class,STRING),
    // FIXME: This should be Instant or ZonedDateTime (but zone id is not specificed in RFC
    Time("time","time as defined by full-time - RFC3339", String.class, STRING),
    Uint8("uint8","unsigned 8-bit integer", Integer.class, INTEGER) {
        @Override
        public JsonNode toWireValue(Object value) throws IllegalArgumentException {
            if (value instanceof Integer val && val >= 0 && val <= 255) {
                return baseMapping.toWireValue(value);
            }
            throw cannotConvertToWire(value);
        }
    },
    UriReference("uri-reference","A URI reference as defined in RFC3986", String.class, STRING),
    UriTemplate("uri-template","A URI Template as defined in RFC6570", String.class, STRING),
    Uri("uri","A Uniform Resource Identifier as defined in RFC3986", String.class, STRING),
    Uuid("uuid", "A Universally Unique Identifier as defined in RFC4122", String.class, STRING);

    protected final String openApiFormat;
    protected final Set<Class<? extends JsonNode>> availableWireTypes;
    protected final Class<?> connidClass;
    protected final Class<? extends JsonNode> primaryWireType;
    protected final JsonSchemaValueMapping baseMapping;

    @SafeVarargs
    OpenApiValueMapping(String openApiFormat, String description, Class<?> connidClass, JsonSchemaValueMapping baseMapping, Class<? extends JsonNode>... jsonClass) {
        this.openApiFormat = openApiFormat;
        this.baseMapping = baseMapping;
        this.connidClass = connidClass;
        if (jsonClass != null && jsonClass.length > 0) {
            this.primaryWireType = baseMapping.primaryWireType();
            this.availableWireTypes = baseMapping.supportedWireTypes();
        } else {
            this.primaryWireType = baseMapping.primaryWireType();
            this.availableWireTypes = baseMapping.supportedWireTypes();
        }
    }



    public Set<Class<? extends JsonNode>> getJsonClasses() {
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
    public Class<? extends JsonNode> primaryWireType() {
        return primaryWireType;
    }

    @Override
    public Set<Class<? extends JsonNode>> supportedWireTypes() {
        return availableWireTypes;
    }

    @Override
    public JsonNode toWireValue(Object value) throws IllegalArgumentException {
        return baseMapping.toWireValue(value);
    }

    @Override
    public Object toConnIdValue(JsonNode value) throws IllegalArgumentException {
        return baseMapping.toConnIdValue(value);
    }

    public static JsonValueMapping from(String jsonType, String openApiFormat) {
        for (OpenApiValueMapping am : values()) {
            if (am.openApiFormat.equals(openApiFormat)) {
                return am;
            }
        };
        return JsonSchemaValueMapping.from(jsonType);
    }

    protected IllegalArgumentException cannotConvertToWire(Object value) {
        return new IllegalArgumentException("Cannot convert " + value.getClass() + " to " + openApiFormat);
    }

        protected IllegalArgumentException cannotConvertToConnId(Object value) {
            return new IllegalArgumentException("Cannot convert " + value.getClass() + " to " + connidClass.getSimpleName());
        }
}
