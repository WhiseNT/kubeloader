package com.whisent.kubeloader.graal.probe.jsdoc;

public final class JsDocParseContext {
    public NamedTagPayload readNamedPayload(String line, String tag) {
        String remainder = extractRemainder(line, tag);
        if (remainder == null) {
            return null;
        }

        TypeExtraction typeExtraction = extractLeadingType(remainder);
        remainder = typeExtraction.remainder().trim();
        boolean readonly = false;

        if (remainder.contains("@readonly")) {
            remainder = remainder.replace("@readonly", "").trim();
            readonly = true;
        }

        if (remainder.isEmpty()) {
            return null;
        }

        String[] parts = remainder.split("\\s+", 2);
        String nameToken = parts[0].trim();
        String description = parts.length > 1 ? stripLeadingDash(parts[1].trim()) : null;

        boolean rest = false;
        boolean optional = false;
        String defaultValue = null;

        if (nameToken.startsWith("...")) {
            rest = true;
            nameToken = nameToken.substring(3).trim();
        }

        if (nameToken.startsWith("[") && nameToken.endsWith("]")) {
            optional = true;
            nameToken = nameToken.substring(1, nameToken.length() - 1).trim();
        }

        int equalsIndex = nameToken.indexOf('=');
        if (equalsIndex >= 0) {
            defaultValue = nameToken.substring(equalsIndex + 1).trim();
            nameToken = nameToken.substring(0, equalsIndex).trim();
            optional = true;
        }

        if (nameToken.isEmpty()) {
            return null;
        }

        return new NamedTagPayload(nameToken, typeExtraction.type(), description, optional, rest, defaultValue, readonly);
    }

    public TypedTagPayload readTypedPayload(String line, String tag) {
        String remainder = extractRemainder(line, tag);
        if (remainder == null) {
            return null;
        }

        TypeExtraction typeExtraction = extractLeadingType(remainder);
        String description = typeExtraction.remainder().trim();
        boolean readonly = false;

        if (description.contains("@readonly")) {
            description = description.replace("@readonly", "").trim();
            readonly = true;
        }

        description = description.isEmpty() ? null : stripLeadingDash(description);
        if ((typeExtraction.type() == null || typeExtraction.type().isBlank()) && description == null && !readonly) {
            return null;
        }

        return new TypedTagPayload(typeExtraction.type(), description, readonly);
    }

    private String extractRemainder(String line, String tag) {
        int tagIndex = line.indexOf(tag);
        if (tagIndex < 0) {
            return null;
        }

        return line.substring(tagIndex + tag.length()).trim();
    }

    private TypeExtraction extractLeadingType(String remainder) {
        if (!remainder.startsWith("{")) {
            return new TypeExtraction(null, remainder);
        }

        int typeEnd = findMatchingBrace(remainder, 0);
        if (typeEnd < 0) {
            return new TypeExtraction(null, remainder);
        }

        String type = remainder.substring(1, typeEnd).trim();
        String tail = remainder.substring(typeEnd + 1).trim();
        return new TypeExtraction(type, tail);
    }

    private int findMatchingBrace(String text, int openBraceIndex) {
        int depth = 0;

        for (int index = openBraceIndex; index < text.length(); index++) {
            char current = text.charAt(index);
            if (current == '{') {
                depth++;
            } else if (current == '}') {
                depth--;
                if (depth == 0) {
                    return index;
                }
            }
        }

        return -1;
    }

    private String stripLeadingDash(String text) {
        if (text == null) {
            return null;
        }

        String trimmed = text.trim();
        if (trimmed.startsWith("-")) {
            trimmed = trimmed.substring(1).trim();
        }

        return trimmed.isEmpty() ? null : trimmed;
    }

    public record NamedTagPayload(String name, String type, String description, boolean optional, boolean rest,
                                  String defaultValue, boolean readonly) {
    }

    public record TypedTagPayload(String type, String description, boolean readonly) {
    }

    private record TypeExtraction(String type, String remainder) {
    }
}