package com.whisent.kubeloader.graal.probe;

import com.whisent.kubeloader.Kubeloader;
import com.whisent.kubeloader.graal.probe.jsdoc.JsDocMetadata;
import com.whisent.kubeloader.graal.probe.jsdoc.JsDocMetadata.JsDocParam;
import com.whisent.kubeloader.graal.probe.jsdoc.JsDocMetadata.JsDocProperty;
import com.whisent.kubeloader.graal.probe.jsdoc.JsDocParser;
import com.whisent.kubeloader.impl.mixin.ScriptManagerInterface;
import com.whisent.kubeloader.klm.dsl.MixinDSL;
import com.whisent.kubeloader.scripts.KLScriptLoader;
import dev.latvian.mods.kubejs.KubeJS;
import dev.latvian.mods.kubejs.script.ScriptFileInfo;
import dev.latvian.mods.kubejs.script.ScriptManager;
import dev.latvian.mods.kubejs.script.ScriptPack;
import dev.latvian.mods.kubejs.script.ScriptPackInfo;
import dev.latvian.mods.kubejs.server.ServerScriptManager;
import net.minecraft.commands.CommandSourceStack;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ProbeDtsGeneratorService {
    private static final JsDocParser JS_DOC_PARSER = JsDocParser.createDefault();
    private static final List<String> SCRIPT_FOLDERS = List.of(
            "startup_scripts",
            "server_scripts",
            "client_scripts",
            Kubeloader.COMMON_SCRIPTS
    );
    private static final Set<String> BUILTIN_TYPE_NAMES = Set.of(
            "any",
            "unknown",
            "never",
            "void",
            "undefined",
            "null",
            "boolean",
            "number",
            "string",
            "symbol",
            "bigint",
            "object",
            "Object",
            "Function",
            "Array",
            "Record<string, any>",
            "Promise<any>"
    );

    private ProbeDtsGeneratorService() {
    }

    public static GenerationResult generate(CommandSourceStack source, LocalContentPackResolver.ResolvedPack pack)
            throws IOException {
        String namespaceToken = sanitizeIdentifier(pack.packId());
        Path generatedDir = pack.rootPath().resolve("probe").resolve("generated");
        List<String> warnings = new ArrayList<>();
        int generatedFiles = 0;
        int failedFiles = 0;

        deleteDirectory(generatedDir);
        Files.createDirectories(generatedDir);

        for (String folderName : SCRIPT_FOLDERS) {
            ScriptManager manager = managerForFolder(folderName);
            if (manager == null) {
                warnings.add("Skipping " + folderName + " because its script manager is unavailable");
                continue;
            }

            Path folderPath = pack.rootPath().resolve(folderName);
            if (!Files.isDirectory(folderPath)) {
                continue;
            }

            String scriptNamespace = namespaceForFolder(pack.packId(), folderName);
            ScriptPack viewPack = new ScriptPack(manager, new ScriptPackInfo(scriptNamespace, ""));
            Map<String, List<MixinDSL>> mixinMap = mixinMapFor(manager);

            try (var stream = Files.walk(folderPath)) {
                List<Path> files = stream.filter(Files::isRegularFile)
                        .filter(ProbeDtsGeneratorService::isScriptFile)
                        .filter(path -> !path.getFileName().toString().endsWith(".d.ts"))
                        .sorted()
                        .toList();

                for (Path scriptFile : files) {
                    Path relativeToFolder = folderPath.relativize(scriptFile);
                    Path relativeToPack = pack.rootPath().relativize(scriptFile);

                    try {
                        String rawSource = Files.readString(scriptFile);
                        ScriptFileInfo info = new ScriptFileInfo(viewPack.info, normalizePath(relativeToFolder));
                        String preparedSource = KLScriptLoader.prepareSourceCode(viewPack, info, mixinMap, rawSource);
                        String declaration = buildScriptDeclaration(namespaceToken, normalizePath(relativeToPack), rawSource, preparedSource);

                        if (declaration == null || declaration.isBlank()) {
                            continue;
                        }

                        Path outputFile = generatedDir.resolve(changeExtension(relativeToPack));
                        if (outputFile.getParent() != null) {
                            Files.createDirectories(outputFile.getParent());
                        }

                        Files.writeString(outputFile, declaration, StandardCharsets.UTF_8);
                        generatedFiles++;
                    } catch (Exception exception) {
                        failedFiles++;
                        warnings.add("Skipped " + normalizePath(relativeToPack) + ": " + describeException(exception));
                    }
                }
            }
        }

        return new GenerationResult(namespaceToken, generatedFiles, failedFiles, generatedDir, warnings);
    }

    private static String buildScriptDeclaration(String namespaceToken, String relativePath, String rawSource,
                                                 String preparedSource) {
        DeclarationExtractor.ExtractionResult extraction = DeclarationExtractor.extract(rawSource, preparedSource);
        if (extraction.declarations().isEmpty() && extraction.sharedBindings().isEmpty()) {
            return null;
        }

        StringBuilder builder = new StringBuilder();
        builder.append("// Generated by /kl probe gen\n");
        builder.append("// Source: ").append(relativePath).append("\n\n");

        appendNamespaceOpen(builder, namespaceToken);
        for (DeclarationExtractor.DeclarationSnippet declaration : extraction.declarations()) {
            if (declaration.jsDoc() != null) {
                builder.append(indent(declaration.jsDoc(), 1)).append("\n");
            }

            builder.append(indent(declaration.declaration(), 1)).append("\n\n");
        }
        appendNamespaceClose(builder);

        String sharedDeclaration = buildContentPacksBindingDeclaration(namespaceToken, extraction.sharedBindings());
        if (sharedDeclaration != null) {
            builder.append("\n").append(sharedDeclaration);
        }

        return builder.toString().trim() + "\n";
    }

    private static String buildContentPacksBindingDeclaration(String namespaceToken,
                                                              List<DeclarationExtractor.SharedBindingSnippet> sharedBindings) {
        if (sharedBindings.isEmpty()) {
            return null;
        }

        StringBuilder builder = new StringBuilder();
        builder.append("declare namespace Internal {\n");
        builder.append("    interface ContentPacksBinding {\n");

        for (DeclarationExtractor.SharedBindingSnippet sharedBinding : sharedBindings) {
            builder.append("        getShared(type: Internal.ScriptType_, id: ")
                    .append(quoteTypeString(sharedBinding.id()))
                    .append("): ")
                    .append(qualifySharedType(namespaceToken, sharedBinding.typeName()))
                    .append(";\n");
        }

        builder.append("    }\n");
        builder.append("}\n");
        return builder.toString();
    }

    private static ScriptManager managerForFolder(String folderName) {
        if ("startup_scripts".equals(folderName)) {
            return KubeJS.getStartupScriptManager();
        }

        return ServerScriptManager.instance;
    }

    private static String namespaceForFolder(String packId, String folderName) {
        if (Kubeloader.COMMON_SCRIPTS.equals(folderName)) {
            return packId + "-common";
        }

        return packId;
    }

    private static Map<String, List<MixinDSL>> mixinMapFor(ScriptManager manager) {
        if (manager instanceof ScriptManagerInterface scriptManagerInterface) {
            Map<String, List<MixinDSL>> mixinMap = scriptManagerInterface.getKubeLoader$mixinDSLs();
            return mixinMap == null ? Map.of() : mixinMap;
        }

        return Map.of();
    }

    private static boolean isScriptFile(Path path) {
        String fileName = path.getFileName().toString();
        return fileName.endsWith(".js") || fileName.endsWith(".ts");
    }

    private static Path changeExtension(Path relativePath) {
        Path fileNamePath = relativePath.getFileName();
        if (fileNamePath == null) {
            return relativePath;
        }

        String fileName = fileNamePath.toString();
        int dotIndex = fileName.lastIndexOf('.');
        String nextName = (dotIndex >= 0 ? fileName.substring(0, dotIndex) : fileName) + ".d.ts";
        Path parent = relativePath.getParent();
        return parent == null ? Paths.get(nextName) : parent.resolve(nextName);
    }

    private static void appendNamespaceOpen(StringBuilder builder, String namespaceToken) {
        builder.append("declare namespace ").append(namespaceToken).append(" {\n");
    }

    private static void appendNamespaceClose(StringBuilder builder) {
        builder.append("}\n");
    }

    private static String indent(String value, int level) {
        if (value == null || value.isBlank()) {
            return "";
        }

        String prefix = "    ".repeat(Math.max(0, level));
        StringBuilder builder = new StringBuilder();
        String[] lines = value.split("\\R", -1);
        for (int index = 0; index < lines.length; index++) {
            if (index > 0) {
                builder.append("\n");
            }
            builder.append(prefix).append(lines[index]);
        }
        return builder.toString();
    }

    private static String normalizePath(Path path) {
        return path.toString().replace('\\', '/');
    }

    private static String sanitizeIdentifier(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return "content_pack";
        }

        String sanitized = rawValue.replaceAll("[^A-Za-z0-9_$]", "_");
        if (!sanitized.matches("[A-Za-z_$].*")) {
            sanitized = "_" + sanitized;
        }
        return sanitized;
    }

    private static String quoteTypeString(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static String qualifySharedType(String namespaceToken, String typeName) {
        if (typeName == null || typeName.isBlank()) {
            return "any";
        }

        if (typeName.contains(".") || typeName.contains("{") || typeName.contains("|") || typeName.contains("<")
                || BUILTIN_TYPE_NAMES.contains(typeName)) {
            return typeName;
        }

        return namespaceToken + "." + typeName;
    }

    private static String describeException(Throwable throwable) {
        if (throwable == null) {
            return "Unknown error";
        }

        return throwable.getMessage() == null || throwable.getMessage().isBlank()
                ? throwable.getClass().getSimpleName()
                : throwable.getMessage();
    }

    private static void deleteDirectory(Path directory) throws IOException {
        if (directory == null || Files.notExists(directory)) {
            return;
        }

        try (var stream = Files.walk(directory)) {
            List<Path> paths = stream.sorted((left, right) -> right.getNameCount() - left.getNameCount()).toList();
            for (Path path : paths) {
                Files.deleteIfExists(path);
            }
        }
    }

    public record GenerationResult(String namespaceToken, int generatedFiles, int failedFiles, Path outputDirectory,
                                   List<String> warnings) {
    }

    private static final class DeclarationExtractor {
        private static final Pattern FUNCTION_PATTERN = Pattern.compile("(?m)^\\s*function\\s+([A-Za-z_$][\\w$]*)\\s*\\(([^)]*)\\)\\s*\\{");
        private static final Pattern CLASS_PATTERN = Pattern.compile("(?m)^\\s*class\\s+([A-Za-z_$][\\w$]*)\\b");
        private static final Pattern INTERFACE_PATTERN = Pattern.compile("(?m)^\\s*(?:export\\s+)?interface\\s+([A-Za-z_$][\\w$]*)\\b");
        private static final Pattern ENUM_PATTERN = Pattern.compile("(?m)^\\s*(?:export\\s+)?enum\\s+([A-Za-z_$][\\w$]*)\\b");
        private static final Pattern TYPE_PATTERN = Pattern.compile("(?m)^\\s*(?:export\\s+)?type\\s+([A-Za-z_$][\\w$]*)\\b");
        private static final Pattern VARIABLE_START_PATTERN = Pattern.compile("(?m)^\\s*(const|let|var)\\s+([A-Za-z_$][\\w$]*)\\s*=\\s*");
        private static final Pattern OBJECT_VARIABLE_PATTERN = Pattern.compile("(?m)^\\s*(const|let|var)\\s+([A-Za-z_$][\\w$]*)\\s*=\\s*\\{");
        private static final Pattern PROTOTYPE_METHOD_PATTERN = Pattern.compile("(?m)^\\s*([A-Za-z_$][\\w$]*)\\.prototype\\.([A-Za-z_$][\\w$]*)\\s*=\\s*function\\s*\\(([^)]*)\\)\\s*\\{");
        private static final Pattern JSDOC_BLOCK_PATTERN = Pattern.compile("/\\*\\*(?:.|\\R)*?\\*/");
        private static final Pattern THIS_ASSIGNMENT_PATTERN = Pattern.compile("(?m)\\bthis\\.([A-Za-z_$][\\w$]*)\\s*=\\s*([^;]+);");
        private static final Pattern PUT_SHARED_PATTERN = Pattern.compile("ContentPacks\\.putShared\\s*\\(\\s*([\"'])((?:\\\\.|(?!\\1).)*)\\1\\s*,\\s*([A-Za-z_$][\\w$]*)\\s*\\)");
        private static final Pattern RETURN_PATTERN = Pattern.compile("\\breturn\\s+([^;]+);");
        private static final Pattern FUNCTION_VALUE_PATTERN = Pattern.compile("^(?:async\\s+)?function(?:\\s+[A-Za-z_$][\\w$]*)?\\s*\\(([^)]*)\\)\\s*\\{");
        private static final Pattern OBJECT_METHOD_PATTERN = Pattern.compile("^([A-Za-z_$][\\w$]*)\\s*\\(([^)]*)\\)\\s*\\{");

        private DeclarationExtractor() {
        }

        private static ExtractionResult extract(String rawSource, String preparedSource) {
            List<DeclarationSnippet> declarations = new ArrayList<>();
            List<SharedBindingSnippet> sharedBindings = new ArrayList<>();
            Set<String> seen = new LinkedHashSet<>();
            Map<String, String> objectTypes = new LinkedHashMap<>();
            int[] rawDepths = computeDepths(rawSource);
            int[] preparedDepths = computeDepths(preparedSource);

            collectConstructorDeclarations(rawSource, rawDepths, declarations, seen, objectTypes);
            collectBlockDeclarations(rawSource, rawDepths, INTERFACE_PATTERN, declarations, seen);
            collectBlockDeclarations(rawSource, rawDepths, ENUM_PATTERN, declarations, seen);
            collectTypeAliases(rawSource, rawDepths, declarations, seen);
            collectDocBlockDeclarations(rawSource, rawDepths, declarations, seen);
            collectObjectDeclarations(rawSource, rawDepths, declarations, seen, objectTypes);
            collectStubDeclarations(preparedSource, preparedDepths, FUNCTION_PATTERN, "function", declarations, seen);
            collectStubDeclarations(preparedSource, preparedDepths, CLASS_PATTERN, "class", declarations, seen);
            collectVariables(preparedSource, preparedDepths, declarations, seen);
            collectSharedDeclarations(rawSource, rawDepths, objectTypes, sharedBindings);

            return new ExtractionResult(declarations, sharedBindings);
        }

        private static void collectConstructorDeclarations(String source, int[] depths,
                                                           List<DeclarationSnippet> declarations, Set<String> seen,
                                                           Map<String, String> objectTypes) {
            Map<String, ConstructorShape> constructors = new LinkedHashMap<>();
            Matcher functionMatcher = FUNCTION_PATTERN.matcher(source);
            while (functionMatcher.find()) {
                if (!isTopLevel(depths, functionMatcher.start())) {
                    continue;
                }

                String name = functionMatcher.group(1);
                int openBraceIndex = source.indexOf('{', functionMatcher.end() - 1);
                int closeBraceIndex = findMatchingBrace(source, openBraceIndex);
                if (openBraceIndex < 0 || closeBraceIndex < 0) {
                    continue;
                }

                JsDocMetadata jsDoc = extractLeadingJsDocMetadata(source, functionMatcher.start());
                ConstructorShape shape = constructors.computeIfAbsent(name, ignored -> new ConstructorShape(
                        name,
                        buildTypedParameterList(functionMatcher.group(2), jsDoc),
                        formatJsDoc(jsDoc, JsDocStyle.DECLARATION)
                ));

                String body = source.substring(openBraceIndex + 1, closeBraceIndex);
                Matcher fieldMatcher = THIS_ASSIGNMENT_PATTERN.matcher(body);
                while (fieldMatcher.find()) {
                    JsDocMetadata fieldDoc = extractLeadingJsDocMetadata(body, fieldMatcher.start());
                    String fieldType = fieldTypeFor(fieldDoc, fieldMatcher.group(2));
                    shape.fields.putIfAbsent(fieldMatcher.group(1), fieldType);
                    shape.fieldShapes.putIfAbsent(fieldMatcher.group(1), new FieldShape(
                            fieldType,
                            formatJsDoc(fieldDoc, JsDocStyle.PROPERTY),
                            fieldDoc != null && fieldDoc.readonly()
                    ));
                }
            }

            Matcher prototypeMatcher = PROTOTYPE_METHOD_PATTERN.matcher(source);
            while (prototypeMatcher.find()) {
                if (!isTopLevel(depths, prototypeMatcher.start())) {
                    continue;
                }

                String constructorName = prototypeMatcher.group(1);
                int openBraceIndex = source.indexOf('{', prototypeMatcher.end() - 1);
                int closeBraceIndex = findMatchingBrace(source, openBraceIndex);
                if (openBraceIndex < 0 || closeBraceIndex < 0) {
                    continue;
                }

                String methodName = prototypeMatcher.group(2);
                JsDocMetadata jsDoc = extractLeadingJsDocMetadata(source, prototypeMatcher.start());
                ConstructorShape shape = constructors.computeIfAbsent(constructorName,
                        ignored -> new ConstructorShape(constructorName, "", null));
                String body = source.substring(openBraceIndex + 1, closeBraceIndex);
                shape.methods.putIfAbsent(methodName, new MethodShape(
                        buildTypedParameterList(prototypeMatcher.group(3), jsDoc),
                        returnTypeFor(jsDoc, inferFunctionReturnType(body)),
                        formatJsDoc(jsDoc, JsDocStyle.FUNCTION)
                ));
            }

            for (ConstructorShape shape : constructors.values()) {
                if (shape.fields.isEmpty() && shape.methods.isEmpty()) {
                    continue;
                }

                if (!seen.add(shape.name)) {
                    continue;
                }

                objectTypes.put(shape.name, shape.name);
                declarations.add(new DeclarationSnippet(null, renderConstructorDeclaration(shape)));
            }
        }

        private static String renderConstructorDeclaration(ConstructorShape shape) {
            StringBuilder builder = new StringBuilder();
            if (shape.jsDoc != null) {
                builder.append(shape.jsDoc).append("\n");
            }

            builder.append("interface ").append(shape.name).append(" {\n");
            for (Map.Entry<String, String> field : shape.fields.entrySet()) {
                FieldShape fieldShape = shape.fieldShapes.get(field.getKey());
                if (fieldShape != null && fieldShape.jsDoc() != null) {
                    builder.append(indent(fieldShape.jsDoc(), 1)).append("\n");
                }

                builder.append("    ");
                if (fieldShape != null && fieldShape.readonly()) {
                    builder.append("readonly ");
                }
                builder.append(renderPropertyName(field.getKey())).append(": ").append(field.getValue()).append(";\n");
            }

            for (Map.Entry<String, MethodShape> method : shape.methods.entrySet()) {
                if (method.getValue().jsDoc() != null) {
                    builder.append(indent(method.getValue().jsDoc(), 1)).append("\n");
                }

                builder.append("    ")
                        .append(renderPropertyName(method.getKey()))
                        .append("(")
                        .append(method.getValue().parameters())
                        .append("): ")
                        .append(method.getValue().returnType())
                        .append(";\n");
            }
            builder.append("}\n\n");
            builder.append("const ").append(shape.name).append(": {\n");
            builder.append("    new(").append(shape.parameters).append("): ").append(shape.name).append(";\n");
            builder.append("    prototype: ").append(shape.name).append(";\n");
            builder.append("};");
            return builder.toString();
        }

        private static void collectBlockDeclarations(String source, int[] depths, Pattern pattern,
                                                     List<DeclarationSnippet> declarations, Set<String> seen) {
            Matcher matcher = pattern.matcher(source);
            while (matcher.find()) {
                if (!isTopLevel(depths, matcher.start())) {
                    continue;
                }

                String name = normalizeDeclarationName(matcher.group(1));
                if (!seen.add(name)) {
                    continue;
                }

                int openBraceIndex = source.indexOf('{', matcher.end());
                int closeBraceIndex = findMatchingBrace(source, openBraceIndex);
                if (openBraceIndex < 0 || closeBraceIndex < 0) {
                    continue;
                }

                JsDocMetadata jsDoc = extractLeadingJsDocMetadata(source, matcher.start());
                String declaration = cleanupDeclaration(source.substring(matcher.start(), closeBraceIndex + 1));
                declarations.add(new DeclarationSnippet(formatJsDoc(jsDoc, JsDocStyle.DECLARATION), declaration));
            }
        }

        private static void collectTypeAliases(String source, int[] depths, List<DeclarationSnippet> declarations,
                                               Set<String> seen) {
            Matcher matcher = TYPE_PATTERN.matcher(source);
            while (matcher.find()) {
                if (!isTopLevel(depths, matcher.start())) {
                    continue;
                }

                String name = normalizeDeclarationName(matcher.group(1));
                if (!seen.add(name)) {
                    continue;
                }

                int statementEnd = findStatementEnd(source, matcher.end());
                JsDocMetadata jsDoc = extractLeadingJsDocMetadata(source, matcher.start());
                String declaration = cleanupDeclaration(source.substring(matcher.start(), statementEnd).trim());
                if (!declaration.endsWith(";")) {
                    declaration = declaration + ";";
                }

                declarations.add(new DeclarationSnippet(formatJsDoc(jsDoc, JsDocStyle.DECLARATION), declaration));
            }
        }

        private static void collectDocBlockDeclarations(String source, int[] depths,
                                                        List<DeclarationSnippet> declarations, Set<String> seen) {
            Matcher matcher = JSDOC_BLOCK_PATTERN.matcher(source);
            while (matcher.find()) {
                if (!isTopLevel(depths, matcher.start())) {
                    continue;
                }

                JsDocMetadata jsDoc = parseJsDoc(matcher.group());
                if (jsDoc == null) {
                    continue;
                }

                if (jsDoc.callbackName() != null && !jsDoc.callbackName().isBlank()) {
                    String callbackName = normalizeDeclarationName(jsDoc.callbackName());
                    if (!seen.add(callbackName)) {
                        continue;
                    }

                    String declaration = "type " + callbackName + " = (" +
                            buildTypedParameterList(jsDoc.params()) + ") => " + returnTypeFor(jsDoc, "void") + ";";
                    declarations.add(new DeclarationSnippet(formatJsDoc(jsDoc, JsDocStyle.FUNCTION), declaration));
                    continue;
                }

                if (jsDoc.typedefName() == null || jsDoc.typedefName().isBlank()) {
                    continue;
                }

                String typedefName = normalizeDeclarationName(jsDoc.typedefName());
                if (!seen.add(typedefName)) {
                    continue;
                }

                PropertyTreeNode propertyTree = buildPropertyTree(jsDoc.properties());
                if (propertyTree != null && !propertyTree.children.isEmpty()) {
                    StringBuilder builder = new StringBuilder();
                    builder.append("interface ").append(typedefName).append(" {\n");
                    appendPropertyTree(builder, propertyTree, 1);
                    builder.append("}");
                    declarations.add(new DeclarationSnippet(formatJsDoc(jsDoc, JsDocStyle.DECLARATION), builder.toString()));
                } else {
                    String typedefType = normalizeJsDocType(jsDoc.typedefType());
                    declarations.add(new DeclarationSnippet(
                            formatJsDoc(jsDoc, JsDocStyle.DECLARATION),
                            "type " + typedefName + " = " + (typedefType == null ? "any" : typedefType) + ";"
                    ));
                }
            }
        }

        private static void collectObjectDeclarations(String source, int[] depths,
                                                      List<DeclarationSnippet> declarations, Set<String> seen,
                                                      Map<String, String> objectTypes) {
            Matcher matcher = OBJECT_VARIABLE_PATTERN.matcher(source);
            while (matcher.find()) {
                if (!isTopLevel(depths, matcher.start())) {
                    continue;
                }

                String kind = matcher.group(1);
                String name = matcher.group(2);
                if (!seen.add(name)) {
                    continue;
                }

                int openBraceIndex = source.indexOf('{', matcher.end() - 1);
                int closeBraceIndex = findMatchingBrace(source, openBraceIndex);
                if (openBraceIndex < 0 || closeBraceIndex < 0) {
                    continue;
                }

                JsDocMetadata jsDoc = extractLeadingJsDocMetadata(source, matcher.start());
                String body = source.substring(openBraceIndex + 1, closeBraceIndex);
                List<ObjectPropertySnippet> properties = collectObjectProperties(body);
                if (properties.isEmpty()) {
                    declarations.add(new DeclarationSnippet(
                            formatJsDoc(jsDoc, JsDocStyle.DECLARATION),
                            kind + " " + name + ": Record<string, any>;"
                    ));
                    objectTypes.put(name, "Record<string, any>");
                    continue;
                }

                StringBuilder builder = new StringBuilder();
                builder.append("interface ").append(name).append(" {\n");
                for (ObjectPropertySnippet property : properties) {
                    if (property.jsDoc() != null) {
                        builder.append(indent(property.jsDoc(), 1)).append("\n");
                    }
                    builder.append("    ").append(property.declaration()).append("\n");
                }
                builder.append("}\n\n");
                builder.append(kind).append(" ").append(name).append(": ").append(name).append(";");

                declarations.add(new DeclarationSnippet(formatJsDoc(jsDoc, JsDocStyle.DECLARATION), builder.toString()));
                objectTypes.put(name, name);
            }
        }

        private static void collectStubDeclarations(String source, int[] depths, Pattern pattern, String kind,
                                                    List<DeclarationSnippet> declarations, Set<String> seen) {
            Matcher matcher = pattern.matcher(source);
            while (matcher.find()) {
                if (!isTopLevel(depths, matcher.start())) {
                    continue;
                }

                String name = matcher.group(1);
                if (!seen.add(name)) {
                    continue;
                }

                if ("function".equals(kind)) {
                    JsDocMetadata jsDoc = extractLeadingJsDocMetadata(source, matcher.start());
                    declarations.add(new DeclarationSnippet(
                            formatJsDoc(jsDoc, JsDocStyle.FUNCTION),
                            "function " + name + "(" + buildTypedParameterList(matcher.group(2), jsDoc) + "): "
                                    + returnTypeFor(jsDoc, "any") + ";"
                    ));
                } else if ("class".equals(kind)) {
                    JsDocMetadata jsDoc = extractLeadingJsDocMetadata(source, matcher.start());
                    declarations.add(new DeclarationSnippet(
                            formatJsDoc(jsDoc, JsDocStyle.DECLARATION),
                            "class " + name + " {}"
                    ));
                }
            }
        }

        private static void collectVariables(String source, int[] depths, List<DeclarationSnippet> declarations,
                                             Set<String> seen) {
            Matcher matcher = VARIABLE_START_PATTERN.matcher(source);
            while (matcher.find()) {
                if (!isTopLevel(depths, matcher.start())) {
                    continue;
                }

                String kind = matcher.group(1);
                String name = matcher.group(2);
                if (!seen.add(name)) {
                    continue;
                }

                int statementEnd = findStatementEnd(source, matcher.end());
                String value = source.substring(matcher.end(), statementEnd).trim();
                if (value.startsWith("{")) {
                    seen.remove(name);
                    continue;
                }

                JsDocMetadata jsDoc = extractLeadingJsDocMetadata(source, matcher.start());
                declarations.add(new DeclarationSnippet(
                        formatJsDoc(jsDoc, JsDocStyle.DECLARATION),
                        kind + " " + name + ": " + fieldTypeFor(jsDoc, value) + ";"
                ));
            }
        }

        private static void collectSharedDeclarations(String source, int[] depths, Map<String, String> objectTypes,
                                                      List<SharedBindingSnippet> sharedBindings) {
            Map<String, SharedBindingSnippet> deduplicated = new LinkedHashMap<>();
            Matcher matcher = PUT_SHARED_PATTERN.matcher(source);
            while (matcher.find()) {
                if (!isTopLevel(depths, matcher.start())) {
                    continue;
                }

                String id = matcher.group(2);
                String objectName = matcher.group(3);
                String typeName = objectTypes.getOrDefault(objectName, objectName);
                deduplicated.putIfAbsent(id, new SharedBindingSnippet(id, typeName));
            }

            sharedBindings.addAll(deduplicated.values());
        }

        private static JsDocMetadata parseJsDoc(String comment) {
            return JS_DOC_PARSER.parse(comment);
        }

        private static JsDocMetadata extractLeadingJsDocMetadata(String source, int declarationStart) {
            String comment = extractLeadingJsDoc(source, declarationStart);
            return comment == null ? null : parseJsDoc(comment);
        }

        private static String extractLeadingJsDoc(String source, int declarationStart) {
            if (source == null || declarationStart <= 0) {
                return null;
            }

            int commentEnd = source.lastIndexOf("*/", declarationStart);
            if (commentEnd < 0) {
                return null;
            }

            int commentStart = source.lastIndexOf("/**", commentEnd);
            if (commentStart < 0) {
                return null;
            }

            String between = source.substring(commentEnd + 2, declarationStart);
            if (!between.trim().isEmpty()) {
                return null;
            }

            return source.substring(commentStart, commentEnd + 2);
        }

        private static String formatJsDoc(JsDocMetadata metadata, JsDocStyle style) {
            if (metadata == null) {
                return null;
            }

            List<String> lines = new ArrayList<>();
            if (metadata.description() != null && !metadata.description().isBlank()) {
                for (String line : metadata.description().split("\\R")) {
                    String trimmed = line.trim();
                    if (!trimmed.isEmpty()) {
                        lines.add(trimmed);
                    }
                }
            }

            if (style == JsDocStyle.FUNCTION) {
                for (JsDocParam param : metadata.params()) {
                    if (param.name().contains(".")) {
                        continue;
                    }

                    StringBuilder builder = new StringBuilder();
                    builder.append("@param ");
                    if (param.type() != null && !param.type().isBlank()) {
                        builder.append("{").append(normalizeJsDocType(param.type())).append("} ");
                    }
                    builder.append(renderParamDocName(param));
                    if (param.description() != null && !param.description().isBlank()) {
                        builder.append(" ").append(param.description().trim());
                    }
                    lines.add(builder.toString());
                }

                if (metadata.returnsType() != null && !metadata.returnsType().isBlank()) {
                    StringBuilder builder = new StringBuilder();
                    builder.append("@returns {").append(normalizeJsDocType(metadata.returnsType())).append("}");
                    if (metadata.returnsDescription() != null && !metadata.returnsDescription().isBlank()) {
                        builder.append(" ").append(metadata.returnsDescription().trim());
                    }
                    lines.add(builder.toString());
                }
            }

            if (lines.isEmpty()) {
                return null;
            }

            StringBuilder builder = new StringBuilder();
            builder.append("/**\n");
            for (String line : lines) {
                builder.append(" * ").append(line).append("\n");
            }
            builder.append(" */");
            return builder.toString();
        }

        private static String renderParamDocName(JsDocParam param) {
            String name = param.name();
            if (param.optional()) {
                if (param.defaultValue() != null && !param.defaultValue().isBlank()) {
                    name = "[" + name + "=" + param.defaultValue() + "]";
                } else {
                    name = "[" + name + "]";
                }
            }

            if (param.rest()) {
                name = "..." + name;
            }

            return name;
        }

            private static String formatJsDoc(String description) {
            if (description == null || description.isBlank()) {
                return null;
            }

            StringBuilder builder = new StringBuilder();
            builder.append("/**\n");
            for (String line : description.split("\\R")) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    builder.append(" * ").append(trimmed).append("\n");
                }
            }
            builder.append(" */");
            return builder.toString();
        }

        private static String buildTypedParameterList(List<JsDocParam> params) {
            if (params == null || params.isEmpty()) {
                return "";
            }

            List<JsDocParam> directParams = params.stream().filter(param -> !param.name().contains(".")).toList();
            List<String> typedParameters = new ArrayList<>();
            for (JsDocParam param : directParams) {
                String name = sanitizeIdentifier(normalizeParamName(param.name()));
                String type = normalizeJsDocType(param.type());
                String nestedType = buildNestedParamObjectType(param.name(), params);
                if (nestedType != null && (type == null || type.isBlank() || "any".equals(type)
                        || "Record<string, any>".equals(type))) {
                    type = nestedType;
                }

                if (type == null || type.isBlank()) {
                    type = "any";
                }

                if (param.rest() && !type.endsWith("[]")) {
                    type = type + "[]";
                }

                if (param.rest()) {
                    name = "..." + name;
                } else if (param.optional()) {
                    name = name + "?";
                }

                typedParameters.add(name + ": " + type);
            }

            return String.join(", ", typedParameters);
        }

        private static String buildTypedParameterList(String parameterList, JsDocMetadata jsDoc) {
            String trimmed = parameterList == null ? "" : parameterList.trim();
            if (trimmed.isEmpty()) {
                return "";
            }

            List<String> typedParameters = new ArrayList<>();
            List<String> rawParameters = splitTopLevelEntries(trimmed);
            int directParameterIndex = 0;

            for (int index = 0; index < rawParameters.size(); index++) {
                String rawParameter = rawParameters.get(index).trim();
                if (rawParameter.isEmpty()) {
                    continue;
                }

                boolean rest = false;
                if (rawParameter.startsWith("...")) {
                    rest = true;
                    rawParameter = rawParameter.substring(3).trim();
                }

                int equalsIndex = indexOfTopLevelEquals(rawParameter);
                boolean optional = equalsIndex >= 0;
                String nameCandidate = equalsIndex >= 0 ? rawParameter.substring(0, equalsIndex).trim() : rawParameter;
                String normalizedName = normalizeParameterCandidate(nameCandidate, index);

                JsDocParam paramDoc = findParamDoc(jsDoc, normalizedName, directParameterIndex);
                if (isIdentifierLike(nameCandidate)) {
                    directParameterIndex++;
                }

                String type = paramTypeFor(paramDoc, normalizedName, jsDoc);
                if (type == null || type.isBlank()) {
                    type = "any";
                }

                boolean docRest = paramDoc != null && paramDoc.rest();
                boolean docOptional = paramDoc != null && (paramDoc.optional() || paramDoc.defaultValue() != null);
                String renderedName = sanitizeIdentifier(normalizedName);
                if (rest || docRest) {
                    renderedName = "..." + renderedName;
                    if (!type.endsWith("[]")) {
                        type = type + "[]";
                    }
                } else if (optional || docOptional) {
                    renderedName = renderedName + "?";
                }

                typedParameters.add(renderedName + ": " + type);
            }

            return String.join(", ", typedParameters);
        }

        private static JsDocParam findParamDoc(JsDocMetadata jsDoc, String parameterName, int parameterIndex) {
            if (jsDoc == null) {
                return null;
            }

            for (JsDocParam param : jsDoc.params()) {
                if (param.name().contains(".")) {
                    continue;
                }
                if (normalizeParamName(param.name()).equals(parameterName)) {
                    return param;
                }
            }

            int currentIndex = 0;
            for (JsDocParam param : jsDoc.params()) {
                if (param.name().contains(".")) {
                    continue;
                }
                if (currentIndex == parameterIndex) {
                    return param;
                }
                currentIndex++;
            }

            return null;
        }

        private static String paramTypeFor(JsDocParam param, String parameterName, JsDocMetadata jsDoc) {
            if (param == null) {
                return "any";
            }

            String type = normalizeJsDocType(param.type());
            String nestedType = buildNestedParamObjectType(parameterName, jsDoc == null ? List.of() : jsDoc.params());
            if (nestedType != null && (type == null || type.isBlank() || "any".equals(type)
                    || "Record<string, any>".equals(type))) {
                type = nestedType;
            }

            return type == null || type.isBlank() ? "any" : type;
        }

        private static String buildNestedParamObjectType(String parameterName, List<JsDocParam> params) {
            PropertyTreeNode root = new PropertyTreeNode();
            String prefix = parameterName + ".";
            for (JsDocParam param : params) {
                if (!param.name().startsWith(prefix)) {
                    continue;
                }

                String nestedName = param.name().substring(prefix.length());
                String[] segments = nestedName.split("\\.");
                PropertyTreeNode node = root;
                for (String segment : segments) {
                    node = node.children.computeIfAbsent(segment, ignored -> new PropertyTreeNode());
                }
                node.type = normalizeJsDocType(param.type());
                node.description = param.description();
                node.optional = param.optional();
            }

            if (root.children.isEmpty()) {
                return null;
            }

            return renderPropertyTreeType(root);
        }

        private static PropertyTreeNode buildPropertyTree(List<JsDocProperty> properties) {
            if (properties == null || properties.isEmpty()) {
                return null;
            }

            PropertyTreeNode root = new PropertyTreeNode();
            for (JsDocProperty property : properties) {
                String[] segments = property.name().split("\\.");
                PropertyTreeNode node = root;
                for (String segment : segments) {
                    node = node.children.computeIfAbsent(segment, ignored -> new PropertyTreeNode());
                }

                node.type = normalizeJsDocType(property.type());
                node.description = property.description();
                node.optional = property.optional();
                node.readonly = property.readonly();
            }
            return root;
        }

        private static void appendPropertyTree(StringBuilder builder, PropertyTreeNode node, int indentLevel) {
            for (Map.Entry<String, PropertyTreeNode> entry : node.children.entrySet()) {
                PropertyTreeNode child = entry.getValue();
                if (child.description != null && !child.description.isBlank()) {
                    builder.append(indent(formatJsDoc(child.description), indentLevel)).append("\n");
                }

                builder.append("    ".repeat(Math.max(0, indentLevel)));
                if (child.readonly) {
                    builder.append("readonly ");
                }
                builder.append(renderPropertyName(entry.getKey()));
                if (child.optional) {
                    builder.append("?");
                }
                builder.append(": ");
                if (child.children.isEmpty()) {
                    builder.append(child.type == null || child.type.isBlank() ? "any" : child.type);
                } else {
                    builder.append(renderPropertyTreeType(child));
                }
                builder.append(";\n");
            }
        }

        private static String renderPropertyTreeType(PropertyTreeNode node) {
            List<String> members = new ArrayList<>();
            for (Map.Entry<String, PropertyTreeNode> entry : node.children.entrySet()) {
                PropertyTreeNode child = entry.getValue();
                StringBuilder member = new StringBuilder();
                if (child.readonly) {
                    member.append("readonly ");
                }
                member.append(renderPropertyName(entry.getKey()));
                if (child.optional) {
                    member.append("?");
                }
                member.append(": ");
                if (child.children.isEmpty()) {
                    member.append(child.type == null || child.type.isBlank() ? "any" : child.type);
                } else {
                    member.append(renderPropertyTreeType(child));
                }
                members.add(member.toString());
            }

            return "{ " + String.join("; ", members) + " }";
        }

        private static String normalizeParamName(String name) {
            int dotIndex = name.indexOf('.');
            return dotIndex >= 0 ? name.substring(0, dotIndex) : name;
        }

        private static String normalizeParameterCandidate(String rawParameter, int index) {
            String candidate = rawParameter.trim();
            if (candidate.startsWith("...") ) {
                candidate = candidate.substring(3).trim();
            }

            if (!isIdentifierLike(candidate)) {
                return "arg" + index;
            }

            return candidate;
        }

        private static boolean isIdentifierLike(String text) {
            return text != null && text.trim().matches("[A-Za-z_$][\\w$]*");
        }

        private static String fieldTypeFor(JsDocMetadata jsDoc, String value) {
            if (jsDoc != null && jsDoc.type() != null && !jsDoc.type().isBlank()) {
                return normalizeJsDocType(jsDoc.type());
            }

            return inferExpressionType(value);
        }

        private static String returnTypeFor(JsDocMetadata jsDoc, String fallbackType) {
            if (jsDoc != null && jsDoc.returnsType() != null && !jsDoc.returnsType().isBlank()) {
                return normalizeJsDocType(jsDoc.returnsType());
            }

            return fallbackType == null || fallbackType.isBlank() ? "any" : fallbackType;
        }

        private static String inferFunctionReturnType(String body) {
            Matcher matcher = RETURN_PATTERN.matcher(body);
            if (!matcher.find()) {
                return "void";
            }

            return inferExpressionType(matcher.group(1));
        }

        private static String inferExpressionType(String expression) {
            String trimmed = expression == null ? "" : expression.trim();
            if (trimmed.isEmpty()) {
                return "any";
            }

            if ((trimmed.startsWith("\"") && trimmed.endsWith("\""))
                    || (trimmed.startsWith("'") && trimmed.endsWith("'"))
                    || (trimmed.startsWith("`") && trimmed.endsWith("`"))) {
                return "string";
            }

            if (trimmed.matches("-?\\d+(?:\\.\\d+)?")) {
                return "number";
            }

            if ("true".equals(trimmed) || "false".equals(trimmed)) {
                return "boolean";
            }

            if ("null".equals(trimmed)) {
                return "null";
            }

            if ("undefined".equals(trimmed)) {
                return "undefined";
            }

            if (trimmed.startsWith("[")) {
                return "any[]";
            }

            if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                return "Record<string, any>";
            }

            if (trimmed.startsWith("async ") || trimmed.startsWith("function") || trimmed.contains("=>")) {
                return "(...args: any[]) => any";
            }

            Matcher newMatcher = Pattern.compile("new\\s+([A-Za-z_$][\\w$.]*)").matcher(trimmed);
            if (newMatcher.find()) {
                return normalizeDeclarationName(newMatcher.group(1));
            }

            if (trimmed.startsWith("Promise.")) {
                return "Promise<any>";
            }

            return "any";
        }

        private static String normalizeJsDocType(String rawType) {
            if (rawType == null || rawType.isBlank()) {
                return "any";
            }

            String type = rawType.trim().replace('\r', ' ').replace('\n', ' ').replaceAll("\\s+", " ");
            if ("*".equals(type) || "?".equals(type)) {
                return "any";
            }

            if (type.startsWith("typeof ")) {
                type = type.substring(7).trim();
            }

            if (type.startsWith("(") && type.endsWith(")") && !type.contains("=>")) {
                String candidate = type.substring(1, type.length() - 1).trim();
                if (!candidate.startsWith("{")) {
                    type = candidate;
                }
            }

            type = type.replace("Array.<", "Array<");
            type = type.replace("Promise.<", "Promise<");
            type = type.replace("Object.<", "Record<");
            type = type.replace("object.<", "Record<");
            type = type.replaceAll("\\bString\\b", "string");
            type = type.replaceAll("\\bNumber\\b", "number");
            type = type.replaceAll("\\bBoolean\\b", "boolean");

            if ("Object".equals(type) || "object".equals(type)) {
                type = "Record<string, any>";
            } else if ("Array".equals(type)) {
                type = "any[]";
            } else if ("Function".equals(type)) {
                type = "(...args: any[]) => any";
            }

            if (type.startsWith("{") && type.endsWith("}") && type.contains(":")) {
                return normalizeObjectLiteralType(type);
            }

            return type.trim();
        }

        private static String normalizeObjectLiteralType(String type) {
            String inner = type.substring(1, type.length() - 1).trim();
            if (inner.isEmpty()) {
                return "{}";
            }

            List<String> members = new ArrayList<>();
            for (String entry : splitTopLevelEntries(inner)) {
                String trimmed = entry.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }

                int colonIndex = indexOfTopLevelColon(trimmed);
                if (colonIndex < 0) {
                    members.add(trimmed);
                    continue;
                }

                String propertyName = trimmed.substring(0, colonIndex).trim();
                String propertyType = trimmed.substring(colonIndex + 1).trim();
                members.add(propertyName + ": " + normalizeJsDocType(propertyType));
            }

            return "{ " + String.join("; ", members) + " }";
        }

        private static String normalizeDeclarationName(String rawName) {
            if (rawName == null || rawName.isBlank()) {
                return "AnonymousType";
            }

            String trimmed = rawName.trim();
            int dotIndex = trimmed.lastIndexOf('.');
            if (dotIndex >= 0) {
                trimmed = trimmed.substring(dotIndex + 1);
            }

            return sanitizeIdentifier(trimmed);
        }

        private static String cleanupDeclaration(String declaration) {
            String cleaned = declaration.trim();
            cleaned = cleaned.replaceFirst("^export\\s+", "");
            cleaned = cleaned.replaceFirst("^declare\\s+", "");
            return cleaned;
        }

        private static List<ObjectPropertySnippet> collectObjectProperties(String body) {
            List<ObjectPropertySnippet> properties = new ArrayList<>();
            JsDocMetadata pendingJsDoc = null;

            for (String entry : splitTopLevelEntries(body)) {
                String trimmed = entry.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }

                JsDocMetadata entryJsDoc = null;
                if (trimmed.startsWith("/**")) {
                    int commentEnd = trimmed.indexOf("*/");
                    if (commentEnd >= 0) {
                        entryJsDoc = parseJsDoc(trimmed.substring(0, commentEnd + 2));
                        trimmed = trimmed.substring(commentEnd + 2).trim();
                    }
                }

                if (trimmed.isEmpty()) {
                    pendingJsDoc = entryJsDoc != null ? entryJsDoc : pendingJsDoc;
                    continue;
                }

                JsDocMetadata jsDoc = entryJsDoc != null ? entryJsDoc : pendingJsDoc;
                pendingJsDoc = null;

                int colonIndex = indexOfTopLevelColon(trimmed);
                String declaration;
                if (colonIndex < 0) {
                    declaration = buildObjectShorthandDeclaration(trimmed, jsDoc);
                } else {
                    String name = trimmed.substring(0, colonIndex).trim();
                    String value = trimmed.substring(colonIndex + 1).trim();
                    declaration = buildObjectPropertyDeclaration(name, value, jsDoc);
                }

                properties.add(new ObjectPropertySnippet(declaration, formatJsDoc(jsDoc, JsDocStyle.PROPERTY)));
            }

            return properties;
        }

        private static String buildObjectPropertyDeclaration(String name, String value, JsDocMetadata jsDoc) {
            Matcher functionMatcher = FUNCTION_VALUE_PATTERN.matcher(value);
            if (functionMatcher.find()) {
                int openBraceIndex = value.indexOf('{', functionMatcher.end() - 1);
                int closeBraceIndex = findMatchingBrace(value, openBraceIndex);
                String body = openBraceIndex >= 0 && closeBraceIndex >= 0
                        ? value.substring(openBraceIndex + 1, closeBraceIndex)
                        : "";
                return renderPropertyName(name) + "(" + buildTypedParameterList(functionMatcher.group(1), jsDoc) + "): "
                        + returnTypeFor(jsDoc, inferFunctionReturnType(body)) + ";";
            }

            return renderPropertyName(name) + ": " + fieldTypeFor(jsDoc, value) + ";";
        }

        private static String buildObjectShorthandDeclaration(String value, JsDocMetadata jsDoc) {
            Matcher methodMatcher = OBJECT_METHOD_PATTERN.matcher(value);
            if (methodMatcher.find()) {
                int openBraceIndex = value.indexOf('{', methodMatcher.end() - 1);
                int closeBraceIndex = findMatchingBrace(value, openBraceIndex);
                String body = openBraceIndex >= 0 && closeBraceIndex >= 0
                        ? value.substring(openBraceIndex + 1, closeBraceIndex)
                        : "";
                return renderPropertyName(methodMatcher.group(1)) + "(" + buildTypedParameterList(methodMatcher.group(2), jsDoc)
                        + "): " + returnTypeFor(jsDoc, inferFunctionReturnType(body)) + ";";
            }

            return renderPropertyName(value) + ": any;";
        }

        private static String renderPropertyName(String rawName) {
            String name = rawName.trim();
            if ((name.startsWith("\"") && name.endsWith("\"")) || (name.startsWith("'") && name.endsWith("'"))) {
                name = name.substring(1, name.length() - 1);
            }

            if (name.matches("[A-Za-z_$][\\w$]*")) {
                return name;
            }

            return quoteTypeString(name);
        }

        private static List<String> splitTopLevelEntries(String source) {
            List<String> entries = new ArrayList<>();
            StringBuilder current = new StringBuilder();
            int braceDepth = 0;
            int parenDepth = 0;
            int bracketDepth = 0;
            boolean inSingleQuote = false;
            boolean inDoubleQuote = false;
            boolean inTemplate = false;
            boolean inLineComment = false;
            boolean inBlockComment = false;
            boolean escaped = false;

            for (int index = 0; index < source.length(); index++) {
                char currentChar = source.charAt(index);
                char nextChar = index + 1 < source.length() ? source.charAt(index + 1) : '\0';

                if (inLineComment) {
                    current.append(currentChar);
                    if (currentChar == '\n' || currentChar == '\r') {
                        inLineComment = false;
                    }
                    continue;
                }

                if (inBlockComment) {
                    current.append(currentChar);
                    if (currentChar == '*' && nextChar == '/') {
                        current.append(nextChar);
                        index++;
                        inBlockComment = false;
                    }
                    continue;
                }

                if (inSingleQuote) {
                    current.append(currentChar);
                    if (escaped) {
                        escaped = false;
                    } else if (currentChar == '\\') {
                        escaped = true;
                    } else if (currentChar == '\'') {
                        inSingleQuote = false;
                    }
                    continue;
                }

                if (inDoubleQuote) {
                    current.append(currentChar);
                    if (escaped) {
                        escaped = false;
                    } else if (currentChar == '\\') {
                        escaped = true;
                    } else if (currentChar == '"') {
                        inDoubleQuote = false;
                    }
                    continue;
                }

                if (inTemplate) {
                    current.append(currentChar);
                    if (escaped) {
                        escaped = false;
                    } else if (currentChar == '\\') {
                        escaped = true;
                    } else if (currentChar == '`') {
                        inTemplate = false;
                    }
                    continue;
                }

                if (currentChar == '/' && nextChar == '/') {
                    current.append(currentChar).append(nextChar);
                    index++;
                    inLineComment = true;
                    continue;
                }

                if (currentChar == '/' && nextChar == '*') {
                    current.append(currentChar).append(nextChar);
                    index++;
                    inBlockComment = true;
                    continue;
                }

                if (currentChar == '\'') {
                    current.append(currentChar);
                    inSingleQuote = true;
                    continue;
                }

                if (currentChar == '"') {
                    current.append(currentChar);
                    inDoubleQuote = true;
                    continue;
                }

                if (currentChar == '`') {
                    current.append(currentChar);
                    inTemplate = true;
                    continue;
                }

                if (currentChar == '{') {
                    braceDepth++;
                } else if (currentChar == '}' && braceDepth > 0) {
                    braceDepth--;
                } else if (currentChar == '(') {
                    parenDepth++;
                } else if (currentChar == ')' && parenDepth > 0) {
                    parenDepth--;
                } else if (currentChar == '[') {
                    bracketDepth++;
                } else if (currentChar == ']' && bracketDepth > 0) {
                    bracketDepth--;
                }

                if (currentChar == ',' && braceDepth == 0 && parenDepth == 0 && bracketDepth == 0) {
                    entries.add(current.toString());
                    current.setLength(0);
                } else {
                    current.append(currentChar);
                }
            }

            if (!current.isEmpty()) {
                entries.add(current.toString());
            }

            return entries;
        }

        private static int indexOfTopLevelColon(String source) {
            return indexOfTopLevelCharacter(source, ':');
        }

        private static int indexOfTopLevelEquals(String source) {
            return indexOfTopLevelCharacter(source, '=');
        }

        private static int indexOfTopLevelCharacter(String source, char target) {
            int braceDepth = 0;
            int parenDepth = 0;
            int bracketDepth = 0;
            boolean inSingleQuote = false;
            boolean inDoubleQuote = false;
            boolean inTemplate = false;
            boolean inLineComment = false;
            boolean inBlockComment = false;
            boolean escaped = false;

            for (int index = 0; index < source.length(); index++) {
                char currentChar = source.charAt(index);
                char nextChar = index + 1 < source.length() ? source.charAt(index + 1) : '\0';

                if (inLineComment) {
                    if (currentChar == '\n' || currentChar == '\r') {
                        inLineComment = false;
                    }
                    continue;
                }

                if (inBlockComment) {
                    if (currentChar == '*' && nextChar == '/') {
                        index++;
                        inBlockComment = false;
                    }
                    continue;
                }

                if (inSingleQuote) {
                    if (escaped) {
                        escaped = false;
                    } else if (currentChar == '\\') {
                        escaped = true;
                    } else if (currentChar == '\'') {
                        inSingleQuote = false;
                    }
                    continue;
                }

                if (inDoubleQuote) {
                    if (escaped) {
                        escaped = false;
                    } else if (currentChar == '\\') {
                        escaped = true;
                    } else if (currentChar == '"') {
                        inDoubleQuote = false;
                    }
                    continue;
                }

                if (inTemplate) {
                    if (escaped) {
                        escaped = false;
                    } else if (currentChar == '\\') {
                        escaped = true;
                    } else if (currentChar == '`') {
                        inTemplate = false;
                    }
                    continue;
                }

                if (currentChar == '/' && nextChar == '/') {
                    inLineComment = true;
                    index++;
                    continue;
                }

                if (currentChar == '/' && nextChar == '*') {
                    inBlockComment = true;
                    index++;
                    continue;
                }

                if (currentChar == '\'') {
                    inSingleQuote = true;
                    continue;
                }

                if (currentChar == '"') {
                    inDoubleQuote = true;
                    continue;
                }

                if (currentChar == '`') {
                    inTemplate = true;
                    continue;
                }

                if (currentChar == '{') {
                    braceDepth++;
                    continue;
                }

                if (currentChar == '}' && braceDepth > 0) {
                    braceDepth--;
                    continue;
                }

                if (currentChar == '(') {
                    parenDepth++;
                    continue;
                }

                if (currentChar == ')' && parenDepth > 0) {
                    parenDepth--;
                    continue;
                }

                if (currentChar == '[') {
                    bracketDepth++;
                    continue;
                }

                if (currentChar == ']' && bracketDepth > 0) {
                    bracketDepth--;
                    continue;
                }

                if (currentChar == target && braceDepth == 0 && parenDepth == 0 && bracketDepth == 0) {
                    return index;
                }
            }

            return -1;
        }

        private static int findMatchingBrace(String source, int openBraceIndex) {
            if (openBraceIndex < 0 || openBraceIndex >= source.length() || source.charAt(openBraceIndex) != '{') {
                return -1;
            }

            int depth = 0;
            boolean inSingleQuote = false;
            boolean inDoubleQuote = false;
            boolean inTemplate = false;
            boolean inLineComment = false;
            boolean inBlockComment = false;
            boolean escaped = false;

            for (int index = openBraceIndex; index < source.length(); index++) {
                char currentChar = source.charAt(index);
                char nextChar = index + 1 < source.length() ? source.charAt(index + 1) : '\0';

                if (inLineComment) {
                    if (currentChar == '\n' || currentChar == '\r') {
                        inLineComment = false;
                    }
                    continue;
                }

                if (inBlockComment) {
                    if (currentChar == '*' && nextChar == '/') {
                        index++;
                        inBlockComment = false;
                    }
                    continue;
                }

                if (inSingleQuote) {
                    if (escaped) {
                        escaped = false;
                    } else if (currentChar == '\\') {
                        escaped = true;
                    } else if (currentChar == '\'') {
                        inSingleQuote = false;
                    }
                    continue;
                }

                if (inDoubleQuote) {
                    if (escaped) {
                        escaped = false;
                    } else if (currentChar == '\\') {
                        escaped = true;
                    } else if (currentChar == '"') {
                        inDoubleQuote = false;
                    }
                    continue;
                }

                if (inTemplate) {
                    if (escaped) {
                        escaped = false;
                    } else if (currentChar == '\\') {
                        escaped = true;
                    } else if (currentChar == '`') {
                        inTemplate = false;
                    }
                    continue;
                }

                if (currentChar == '/' && nextChar == '/') {
                    inLineComment = true;
                    index++;
                    continue;
                }

                if (currentChar == '/' && nextChar == '*') {
                    inBlockComment = true;
                    index++;
                    continue;
                }

                if (currentChar == '\'') {
                    inSingleQuote = true;
                    continue;
                }

                if (currentChar == '"') {
                    inDoubleQuote = true;
                    continue;
                }

                if (currentChar == '`') {
                    inTemplate = true;
                    continue;
                }

                if (currentChar == '{') {
                    depth++;
                } else if (currentChar == '}') {
                    depth--;
                    if (depth == 0) {
                        return index;
                    }
                }
            }

            return -1;
        }

        private static int findStatementEnd(String source, int startIndex) {
            int braceDepth = 0;
            int parenDepth = 0;
            int bracketDepth = 0;
            boolean inSingleQuote = false;
            boolean inDoubleQuote = false;
            boolean inTemplate = false;
            boolean inLineComment = false;
            boolean inBlockComment = false;
            boolean escaped = false;

            for (int index = startIndex; index < source.length(); index++) {
                char currentChar = source.charAt(index);
                char nextChar = index + 1 < source.length() ? source.charAt(index + 1) : '\0';

                if (inLineComment) {
                    if (currentChar == '\n' || currentChar == '\r') {
                        inLineComment = false;
                    }
                    continue;
                }

                if (inBlockComment) {
                    if (currentChar == '*' && nextChar == '/') {
                        index++;
                        inBlockComment = false;
                    }
                    continue;
                }

                if (inSingleQuote) {
                    if (escaped) {
                        escaped = false;
                    } else if (currentChar == '\\') {
                        escaped = true;
                    } else if (currentChar == '\'') {
                        inSingleQuote = false;
                    }
                    continue;
                }

                if (inDoubleQuote) {
                    if (escaped) {
                        escaped = false;
                    } else if (currentChar == '\\') {
                        escaped = true;
                    } else if (currentChar == '"') {
                        inDoubleQuote = false;
                    }
                    continue;
                }

                if (inTemplate) {
                    if (escaped) {
                        escaped = false;
                    } else if (currentChar == '\\') {
                        escaped = true;
                    } else if (currentChar == '`') {
                        inTemplate = false;
                    }
                    continue;
                }

                if (currentChar == '/' && nextChar == '/') {
                    inLineComment = true;
                    index++;
                    continue;
                }

                if (currentChar == '/' && nextChar == '*') {
                    inBlockComment = true;
                    index++;
                    continue;
                }

                if (currentChar == '\'') {
                    inSingleQuote = true;
                    continue;
                }

                if (currentChar == '"') {
                    inDoubleQuote = true;
                    continue;
                }

                if (currentChar == '`') {
                    inTemplate = true;
                    continue;
                }

                if (currentChar == '{') {
                    braceDepth++;
                    continue;
                }

                if (currentChar == '}' && braceDepth > 0) {
                    braceDepth--;
                    continue;
                }

                if (currentChar == '(') {
                    parenDepth++;
                    continue;
                }

                if (currentChar == ')' && parenDepth > 0) {
                    parenDepth--;
                    continue;
                }

                if (currentChar == '[') {
                    bracketDepth++;
                    continue;
                }

                if (currentChar == ']' && bracketDepth > 0) {
                    bracketDepth--;
                    continue;
                }

                if (currentChar == ';' && braceDepth == 0 && parenDepth == 0 && bracketDepth == 0) {
                    return index + 1;
                }
            }

            return source.length();
        }

        private static int[] computeDepths(String source) {
            int[] depths = new int[source.length() + 1];
            int depth = 0;
            boolean inSingleQuote = false;
            boolean inDoubleQuote = false;
            boolean inTemplate = false;
            boolean inLineComment = false;
            boolean inBlockComment = false;
            boolean escaped = false;

            for (int index = 0; index < source.length(); index++) {
                depths[index] = depth;
                char currentChar = source.charAt(index);
                char nextChar = index + 1 < source.length() ? source.charAt(index + 1) : '\0';

                if (inLineComment) {
                    if (currentChar == '\n' || currentChar == '\r') {
                        inLineComment = false;
                    }
                    depths[index + 1] = depth;
                    continue;
                }

                if (inBlockComment) {
                    if (currentChar == '*' && nextChar == '/') {
                        index++;
                        depths[index] = depth;
                        inBlockComment = false;
                    }
                    depths[index + 1] = depth;
                    continue;
                }

                if (inSingleQuote) {
                    if (escaped) {
                        escaped = false;
                    } else if (currentChar == '\\') {
                        escaped = true;
                    } else if (currentChar == '\'') {
                        inSingleQuote = false;
                    }
                    depths[index + 1] = depth;
                    continue;
                }

                if (inDoubleQuote) {
                    if (escaped) {
                        escaped = false;
                    } else if (currentChar == '\\') {
                        escaped = true;
                    } else if (currentChar == '"') {
                        inDoubleQuote = false;
                    }
                    depths[index + 1] = depth;
                    continue;
                }

                if (inTemplate) {
                    if (escaped) {
                        escaped = false;
                    } else if (currentChar == '\\') {
                        escaped = true;
                    } else if (currentChar == '`') {
                        inTemplate = false;
                    }
                    depths[index + 1] = depth;
                    continue;
                }

                if (currentChar == '/' && nextChar == '/') {
                    inLineComment = true;
                    depths[index + 1] = depth;
                    continue;
                }

                if (currentChar == '/' && nextChar == '*') {
                    inBlockComment = true;
                    depths[index + 1] = depth;
                    continue;
                }

                if (currentChar == '\'') {
                    inSingleQuote = true;
                    depths[index + 1] = depth;
                    continue;
                }

                if (currentChar == '"') {
                    inDoubleQuote = true;
                    depths[index + 1] = depth;
                    continue;
                }

                if (currentChar == '`') {
                    inTemplate = true;
                    depths[index + 1] = depth;
                    continue;
                }

                if (currentChar == '{') {
                    depth++;
                } else if (currentChar == '}' && depth > 0) {
                    depth--;
                }

                depths[index + 1] = depth;
            }

            return depths;
        }

        private static boolean isTopLevel(int[] depths, int index) {
            if (index < 0 || index >= depths.length) {
                return false;
            }

            return depths[index] == 0;
        }

        private enum JsDocStyle {
            DECLARATION,
            FUNCTION,
            PROPERTY
        }

        private record ExtractionResult(List<DeclarationSnippet> declarations,
                                        List<SharedBindingSnippet> sharedBindings) {
        }

        private record DeclarationSnippet(String jsDoc, String declaration) {
        }

        private record SharedBindingSnippet(String id, String typeName) {
        }

        private static final class ConstructorShape {
            private final String name;
            private final String parameters;
            private final String jsDoc;
            private final Map<String, String> fields = new LinkedHashMap<>();
            private final Map<String, FieldShape> fieldShapes = new LinkedHashMap<>();
            private final Map<String, MethodShape> methods = new LinkedHashMap<>();

            private ConstructorShape(String name, String parameters, String jsDoc) {
                this.name = name;
                this.parameters = parameters == null ? "" : parameters;
                this.jsDoc = jsDoc;
            }
        }

        private record FieldShape(String type, String jsDoc, boolean readonly) {
        }

        private record MethodShape(String parameters, String returnType, String jsDoc) {
        }

        private record ObjectPropertySnippet(String declaration, String jsDoc) {
        }

        private static final class PropertyTreeNode {
            private String type;
            private String description;
            private boolean optional;
            private boolean readonly;
            private final Map<String, PropertyTreeNode> children = new LinkedHashMap<>();
        }
    }
}