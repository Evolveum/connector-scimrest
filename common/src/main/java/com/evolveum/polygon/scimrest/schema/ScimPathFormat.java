/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.scimrest.schema;

import com.evolveum.polygon.conndev.api.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Parser and serializer for SCIM attribute paths as defined by
 * RFC 7643 (attribute names) and RFC 7644 (filter expressions and the
 * PATCH {@code path} rule {@code PATH = attrPath / valuePath [subAttr]}).
 *
 * <p>Supported grammar:</p>
 * <pre>
 * path      = [ uri ":" ] attrName ( "." attrName )* [ "[" filter "]" ( "." attrName )* ]
 * attrName  = ALPHA *("-" / "_" / "$" / DIGIT / ALPHA)
 * filter    = positiveInteger / eqExpr
 * eqExpr    = eqTerm ( "and" eqTerm )*
 * eqTerm    = attrName " eq " jsonValue
 * jsonValue = JSON string / number / true / false / null  (RFC 7159)
 * </pre>
 *
 * <p>Examples: {@code name.givenName},
 * {@code urn:ietf:params:scim:schemas:extension:enterprise:2.0:User:employeeNumber},
 * {@code emails[type eq "work"].value},
 * {@code members[value eq "2819c223-7f76-453a-413861904646"].displayName}.</p>
 *
 * <p>Only the subset of SCIM filters that {@link AttributePath} can represent
 * is accepted: conjunctions of {@code attrName eq value} (and bare array indexes).
 * Other filter operators ({@code ne}, {@code gt}, {@code or}, {@code not},
 * {@code pr}, ...) are rejected with a {@link ParsingException}.</p>
 */
public final class ScimPathFormat implements AttributePathFormat<String> {

    private static final Pattern ATTR_NAME = Pattern.compile("[A-Za-z][A-Za-z0-9_\\-$]*");
    public static final ScimPathFormat INSTANCE = new ScimPathFormat();

    private ScimPathFormat() {
    }

    static {
        StringAttributePathFormats.register("SCIM_PATH", ScimPathFormat.INSTANCE);
    }

    // ==================== Parsing ====================

    /**
     * Parses an SCIM attribute path into an {@link AttributePath}.
     *
     * @param input the SCIM attribute path string
     * @return the parsed path
     * @throws ParsingException if the input is not a valid SCIM attribute path
     */
    public AttributePath parse(String input) {
        if (input == null) {
            throw new ParsingException("SCIM attribute path must not be null");
        }
        var source = input.strip();
        if (source.isEmpty()) {
            throw new ParsingException("Invalid SCIM attribute path: must not be empty");
        }
        var parser = new Parser(source);
        var components = new ArrayList<AttributePath.Component>();

        // The head of the path (everything before the first '[') is either a plain
        // attribute name chain or a schema extension URI prefix ('schemaUri:attributeName').
        // The URI is identified by the last ':' in the head, since URIs (e.g. with a
        // version segment such as '2.0') may contain dots while attribute names may not.
        var firstBracket = source.indexOf('[');
        var head = firstBracket >= 0 ? source.substring(0, firstBracket) : source;
        var lastColon = head.lastIndexOf(':');
        if (lastColon > 0) {
            var uri = head.substring(0, lastColon);
            var segments = splitAndValidate(parser, head.substring(lastColon + 1));
            components.add(new AttributePath.Extension(uri));
            for (var segment : segments) {
                components.add(new AttributePath.Attribute(segment));
            }
        } else {
            var segments = splitAndValidate(parser, head);
            for (var segment : segments) {
                components.add(new AttributePath.Attribute(segment));
            }
        }

        parser.pos = head.length();
        while (!parser.isAtEnd()) {
            var c = parser.peek();
            if (c == '.') {
                parser.pos++;
                var segment = parser.readSegment();
                requireAttrName(parser, segment);
                components.add(new AttributePath.Attribute(segment));
            } else if (c == '[') {
                parseFilter(parser, components);
            } else {
                parser.error("Unexpected character '" + c + "'");
                return null;
            }
        }
        return new AttributePath(List.copyOf(components));
    }

    private static void requireAttrName(Parser parser, String name) {
        if (!ATTR_NAME.matcher(name).matches()) {
            parser.error("Invalid attribute name '" + name + "'");
            return;
        }
    }

    private static List<String> splitAndValidate(Parser parser, String chain) {
        var segments = new ArrayList<String>();
        for (var segment : chain.split("\\.", -1)) {
            if (!ATTR_NAME.matcher(segment).matches()) {
                parser.error("Invalid attribute name '" + segment + "' in '" + chain + "'");
                return null;
            }
            segments.add(segment);
        }
        return segments;
    }

    private static void parseFilter(Parser parser, List<AttributePath.Component> components) {
        parser.expect('[');
        parser.skipSpaces();
        if (!parser.isAtEnd() && Character.isDigit(parser.peek())) {
            var start = parser.pos;
            while (!parser.isAtEnd() && Character.isDigit(parser.peek())) {
                parser.pos++;
            }
            var text = parser.input.substring(start, parser.pos);
            int index;
            try {
                index = Integer.parseInt(text);
            } catch (NumberFormatException e) {
                parser.error("Array index out of range: '" + text + "'");
                return;
            }
            parser.skipSpaces();
            parser.expect(']');
            components.add(new AttributePath.IndexFilter(index));
            return;
        }

        var values = new LinkedHashMap<String, Object>();
        parseEqTerm(parser, values);
        while (true) {
            parser.skipSpaces();
            if (!parser.matchesWord("and")) {
                break;
            }
            parseEqTerm(parser, values);
        }
        parser.skipSpaces();
        parser.expect(']');
        components.add(new AttributePath.SimpleValueFilter(values));
    }

    private static void parseEqTerm(Parser parser, Map<String, Object> values) {
        parser.skipSpaces();
        var key = parser.readNameToken();
        if (!ATTR_NAME.matcher(key).matches()) {
            parser.error("Expected an attribute name in filter, got '" + key + "'");
            return;
        }
        parser.skipSpaces();
        if (!parser.matchesWord("eq")) {
            parser.error("Expected 'eq' in filter");
            return;
        }
        parser.skipSpaces();
        var value = parser.readJsonValue();
        if (values.containsKey(key)) {
            parser.error("Duplicate attribute '" + key + "' in filter");
            return;
        }
        values.put(key, value);
    }

    // ==================== Serialization ====================

    /**
     * Serializes an {@link AttributePath} to SCIM attribute path notation.
     *
     * @param path the path to serialize
     * @return the SCIM attribute path string
     * @throws AttributePathFormatException if the path contains a component that can not be
     *         represented in SCIM notation (an empty path, an extension that is not the first
     *         component, a negative array index, or an invalid attribute name)
     */
    public String serialize(AttributePath path) {
        if (path == null) {
            throw new AttributePathFormatException("Cannot serialize a null path to SCIM notation");
        }
        var components = path.components();
        if (components.isEmpty()) {
            throw new AttributePathFormatException("Cannot serialize an empty path to SCIM notation");
        }
        var sb = new StringBuilder();
        var first = true;
        var previous = (AttributePath.Component) null;
        for (var component : components) {
            switch (component) {
                case AttributePath.Attribute attr -> {
                    requireAttrName(attr.name());
                    if (first) {
                        sb.append(attr.name());
                    } else if (previous instanceof AttributePath.Extension) {
                        sb.append(':').append(attr.name());
                    } else {
                        sb.append('.').append(attr.name());
                    }
                }
                case AttributePath.Extension ext -> {
                    if (!first) {
                        throw new AttributePathFormatException(
                                "SCIM schema extension URI '" + ext.name() + "' must be the first path component");
                    }
                    sb.append(ext.name());
                }
                case AttributePath.IndexFilter index -> {
                    if (index.index() < 0) {
                        throw new AttributePathFormatException(
                                "Negative array index is not valid in SCIM notation: " + index.index());
                    }
                    sb.append('[').append(index.index()).append(']');
                }
                case AttributePath.SimpleValueFilter filter -> {
                    if (filter.keyValues().isEmpty()) {
                        throw new AttributePathFormatException("Cannot serialize an empty value filter to SCIM notation");
                    }
                    sb.append('[');
                    var filterFirst = true;
                    for (var entry : filter.keyValues().entrySet()) {
                        if (!filterFirst) {
                            sb.append(" and ");
                        }
                        filterFirst = false;
                        requireAttrName(entry.getKey());
                        sb.append(entry.getKey()).append(" eq ").append(serializeValue(entry.getValue()));
                    }
                    sb.append(']');
                }
            }
            first = false;
            previous = component;
        }
        return sb.toString();
    }

    private static void requireAttrName(String name) {
        if (!ATTR_NAME.matcher(name).matches()) {
            throw new AttributePathFormatException("Invalid SCIM attribute name: '" + name + "'");
        }
    }

    private static String serializeValue(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String s) {
            return jsonString(s);
        }
        if (value instanceof Boolean b || value instanceof Number n) {
            return value.toString();
        }
        throw new AttributePathFormatException(
                "Cannot serialize SCIM filter value of type: " + value.getClass().getName());
    }

    private static String jsonString(String s) {
        var sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            var c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }

    // ==================== Parser ====================

    private static final class Parser {

        private final String input;
        private int pos;

        private Parser(String input) {
            this.input = input;
        }

        private boolean isAtEnd() {
            return pos >= input.length();
        }

        private char peek() {
            return input.charAt(pos);
        }

        private void skipSpaces() {
            while (!isAtEnd() && input.charAt(pos) == ' ') {
                pos++;
            }
        }

        private void expect(char c) {
            if (isAtEnd() || input.charAt(pos) != c) {
                error("Expected '" + c + "'");
            }
            pos++;
        }

        /**
         * Reads a dot-separated name segment: up to the next '.', '[', or end of input.
         */
        private String readSegment() {
            var start = pos;
            while (!isAtEnd() && input.charAt(pos) != '.' && input.charAt(pos) != '[') {
                pos++;
            }
            return input.substring(start, pos);
        }

        /**
         * Reads a token of attribute-name characters (stops at any other character).
         */
        private String readNameToken() {
            var start = pos;
            while (!isAtEnd() && isNameChar(input.charAt(pos))) {
                pos++;
            }
            return input.substring(start, pos);
        }

        /**
         * Matches a keyword bounded by non-name characters (e.g. {@code eq}, {@code and}, {@code true}).
         */
        private boolean matchesWord(String word) {
            if (input.regionMatches(pos, word, 0, word.length())) {
                var next = pos + word.length();
                if (next >= input.length() || !isNameChar(input.charAt(next))) {
                    pos = next;
                    return true;
                }
            }
            return false;
        }

        private Object readJsonValue() {
            if (isAtEnd()) {
                error("Expected a value in filter");
                return null;
            }
            var c = peek();
            if (c == '"') {
                return readJsonString();
            }
            if (matchesWord("true")) {
                return Boolean.TRUE;
            }
            if (matchesWord("false")) {
                return Boolean.FALSE;
            }
            if (matchesWord("null")) {
                return null;
            }
            var start = pos;
            while (!isAtEnd()) {
                var ch = input.charAt(pos);
                if (Character.isDigit(ch) || ch == '-' || ch == '+' || ch == '.' || ch == 'e' || ch == 'E') {
                    pos++;
                } else {
                    break;
                }
            }
            var text = input.substring(start, pos);
            if (text.isEmpty()) {
                error("Expected a value in filter, got '" + c + "'");
                return null;
            }
            return readNumber(text);
        }

        private String readJsonString() {
            pos++;
            var sb = new StringBuilder();
            while (true) {
                if (isAtEnd()) {
                    error("Unterminated string in filter");
                    return null;
                }
                var c = input.charAt(pos++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c == '\\') {
                    if (isAtEnd()) {
                        error("Unterminated escape in filter string");
                        return null;
                    }
                    var esc = input.charAt(pos++);
                    switch (esc) {
                        case '"' -> sb.append('"');
                        case '\\' -> sb.append('\\');
                        case '/' -> sb.append('/');
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'u' -> {
                            if (pos + 4 > input.length()) {
                                error("Invalid unicode escape in filter string");
                                return null;
                            }
                            try {
                                sb.append((char) Integer.parseInt(input.substring(pos, pos + 4), 16));
                            } catch (NumberFormatException e) {
                                error("Invalid unicode escape in filter string");
                                return null;
                            }
                            pos += 4;
                        }
                        default -> {
                            error("Invalid escape character '\\" + esc + "' in filter string");
                            return null;
                        }
                    }
                } else {
                    sb.append(c);
                }
            }
        }

        private Number readNumber(String text) {
            if (!text.contains(".") && !text.contains("e") && !text.contains("E")) {
                try {
                    return Integer.parseInt(text);
                } catch (NumberFormatException ignored) {
                    try {
                        return Long.parseLong(text);
                    } catch (NumberFormatException ignored2) {
                        // fall through to double
                    }
                }
            }
            try {
                return Double.parseDouble(text);
            } catch (NumberFormatException e) {
                error("Invalid number in filter: '" + text + "'");
                return null;
            }
        }

        private ParsingException error(String message) {
            throw new ParsingException("Invalid SCIM attribute path '" + input + "': " + message
                    + " (position " + pos + ")");
        }

        private static boolean isNameChar(char c) {
            return Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '$';
        }
    }
}
