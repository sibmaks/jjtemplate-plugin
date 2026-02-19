package io.github.sibmaks.jjtemplate.idea.toolwindow;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.intellij.json.JsonFileType;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.content.ContentFactory;
import io.github.sibmaks.jjtemplate.compiler.api.TemplateCompiler;
import io.github.sibmaks.jjtemplate.compiler.api.TemplateScript;
import io.github.sibmaks.jjtemplate.idea.lang.JjtemplateFileType;
import io.github.sibmaks.jjtemplate.lexer.TemplateLexer;
import io.github.sibmaks.jjtemplate.lexer.api.Keyword;
import io.github.sibmaks.jjtemplate.lexer.api.Token;
import io.github.sibmaks.jjtemplate.lexer.api.TokenType;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.regex.Pattern;

public final class JjtemplateSideMenuToolWindowFactory implements ToolWindowFactory, DumbAware {
    private static final Logger LOG = Logger.getInstance(JjtemplateSideMenuToolWindowFactory.class);
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private static final TemplateCompiler COMPILER = TemplateCompiler.getInstance();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        var content = ContentFactory.getInstance().createContent(createCompilerPanel(project), "Compiler", false);
        toolWindow.getContentManager().addContent(content);
    }

    private static @NotNull JPanel createCompilerPanel(@NotNull Project project) {
        try {
            var contextDocument = EditorFactory.getInstance().createDocument("{}");
            var contextInput = new EditorTextField(contextDocument, project, JsonFileType.INSTANCE, false, false);
            contextInput.setPreferredSize(new Dimension(320, 220));

            var panel = new JPanel(new BorderLayout(0, 8));
            panel.add(new JLabel("Context JSON"), BorderLayout.NORTH);
            panel.add(contextInput, BorderLayout.CENTER);

            var actionsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
            var formatContextButton = new JButton("Format JSON");
            formatContextButton.addActionListener(event -> formatActiveFileJson(project));
            var generateContextButton = new JButton("Generate Context");
            generateContextButton.addActionListener(event -> generateContext(project, contextInput));
            var compileButton = new JButton("Compile");
            compileButton.addActionListener(event -> compileCurrentFile(project, contextInput));
            actionsPanel.add(formatContextButton);
            actionsPanel.add(generateContextButton);
            actionsPanel.add(compileButton);
            panel.add(actionsPanel, BorderLayout.SOUTH);
            return panel;
        } catch (Exception exception) {
            var fallback = new JPanel(new BorderLayout());
            fallback.add(new JLabel("Unable to initialize JJTemplate panel: " + getRootMessage(exception)), BorderLayout.NORTH);
            return fallback;
        }
    }

    private static void compileCurrentFile(@NotNull Project project, @NotNull EditorTextField contextInput) {
        try {
            var contextJson = formatContextJson(project, contextInput);
            if (contextJson == null) {
                return;
            }

            var sourceFile = getCurrentJjtemplateFile(project);
            if (sourceFile == null) {
                return;
            }
            var document = FileDocumentManager.getInstance().getDocument(sourceFile);
            if (document == null) {
                showError(project, "Unable to read current file.");
                return;
            }

            var script = MAPPER.readValue(document.getText(), TemplateScript.class);
            var compiled = COMPILER.compile(script);
            var context = MAPPER.readValue(contextJson, MAP_TYPE);
            var rendered = compiled.render(context);
            var output = createPrettyWriter(JjtemplateIndentOptions.getIndent(project)).writeValueAsString(rendered);

            var outputName = sourceFile.getNameWithoutExtension() + "-compiled.json";
            var outputFile = new LightVirtualFile(outputName, JsonFileType.INSTANCE, output);
            outputFile.setWritable(false);
            FileEditorManager.getInstance(project).openFile(outputFile, true, true);
        } catch (Exception exception) {
            LOG.error("JJTemplate compilation failed", exception);
            showError(project, "Compilation failed:\n" + getRootMessage(exception));
        }
    }

    private static void generateContext(@NotNull Project project, @NotNull EditorTextField contextInput) {
        try {
            var sourceFile = getCurrentJjtemplateFile(project);
            if (sourceFile == null) {
                return;
            }

            var document = FileDocumentManager.getInstance().getDocument(sourceFile);
            if (document == null) {
                showError(project, "Unable to read current file.");
                return;
            }

            var contextTemplate = buildContextTemplate(document.getText());
            var pretty = createPrettyWriter(JjtemplateIndentOptions.getIndent(project)).writeValueAsString(contextTemplate);
            contextInput.setText(pretty);
        } catch (Exception exception) {
            showError(project, "Context generation failed:\n" + getRootMessage(exception));
        }
    }

    private static VirtualFile getCurrentJjtemplateFile(@NotNull Project project) {
        var editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
        if (editor == null) {
            showError(project, "Open a JJTemplate file in the editor first.");
            return null;
        }

        var sourceFile = FileDocumentManager.getInstance().getFile(editor.getDocument());
        if (sourceFile == null || isNotJjtemplateFile(sourceFile)) {
            showError(project, "Current file is not a JJTemplate file (*.jjt, *.jjtemplate).");
            return null;
        }
        return sourceFile;
    }

    private static Map<String, Object> buildContextTemplate(@NotNull String source) {
        var root = new LinkedHashMap<String, Object>();
        try {
            var scriptNode = MAPPER.readTree(source);
            var globalDefinitions = extractGlobalDefinitions(scriptNode);
            var script = MAPPER.treeToValue(scriptNode, TemplateScript.class);
            var localDefinitions = collectLocalDefinitions(script, globalDefinitions);
            localDefinitions.addAll(collectExpressionDefinitionNames(script));

            collectContextPaths(root, script.getTemplate(), localDefinitions);
            var definitions = script.getDefinitions();
            if (definitions != null) {
                for (var definition : definitions) {
                    for (var entry : definition.entrySet()) {
                        var definitionLocals = new HashSet<>(localDefinitions);
                        definitionLocals.addAll(extractDefinitionLocalVariables(entry.getKey()));
                        collectContextPaths(root, entry.getKey(), definitionLocals);
                        collectContextPaths(root, entry.getValue(), definitionLocals);
                    }
                }
            }
        } catch (Exception ignored) {
            // Keep empty context if parsing fails.
        }
        return root;
    }

    private static void collectContextPaths(@NotNull Map<String, Object> root,
                                            Object sourceNode,
                                            @NotNull Set<String> localDefinitions) {
        try {
            var templateSource = MAPPER.writeValueAsString(sourceNode);
            var tokens = new TemplateLexer(templateSource).tokens();
            var rangeBindings = collectRangeBindings(tokens);
            for (int i = 0; i < tokens.size(); i++) {
                if (!isExternalRootVariable(tokens, i, localDefinitions, rangeBindings)) {
                    continue;
                }
                addPath(root, readPathSegments(tokens, i));
            }
        } catch (Exception ignored) {
            // Keep partial context if a fragment cannot be parsed.
        }
    }

    private static Set<String> collectLocalDefinitions(@NotNull TemplateScript script,
                                                       @NotNull Set<String> globalDefinitions) {
        var result = new HashSet<String>();
        var definitions = script.getDefinitions();
        if (definitions == null) {
            return result;
        }
        for (var definition : definitions) {
            for (var key : definition.keySet()) {
                if (key != null
                        && IDENTIFIER_PATTERN.matcher(key).matches()
                        && !globalDefinitions.contains(key)) {
                    result.add(key);
                }
            }
        }
        return result;
    }

    private static Set<String> extractDefinitionLocalVariables(String definitionKey) {
        var result = new HashSet<String>();
        if (definitionKey == null || definitionKey.isBlank()) {
            return result;
        }
        if (IDENTIFIER_PATTERN.matcher(definitionKey).matches()) {
            result.add(definitionKey);
            return result;
        }
        try {
            var tokens = new TemplateLexer(definitionKey).tokens();
            for (int i = 0; i < tokens.size(); i++) {
                var token = tokens.get(i);
                if (token.type != TokenType.IDENT) {
                    continue;
                }
                var next = findNextNonTextToken(tokens, i + 1);
                if (next == null || next.type() != TokenType.KEYWORD) {
                    continue;
                }
                if (!Keyword.RANGE.eq(next.token.lexeme) && !Keyword.SWITCH.eq(next.token.lexeme)) {
                    continue;
                }
                result.add(token.lexeme);
                if (Keyword.RANGE.eq(next.token.lexeme)) {
                    collectRangeBindingsForKeyword(tokens, next.index, result);
                }
            }
        } catch (Exception ignored) {
            // Ignore malformed definition key and keep best-effort context.
        }
        return result;
    }

    private static Set<String> collectExpressionDefinitionNames(@NotNull TemplateScript script) {
        var result = new HashSet<String>();
        var definitions = script.getDefinitions();
        if (definitions == null) {
            return result;
        }
        for (var definition : definitions) {
            for (var key : definition.keySet()) {
                if (key == null || key.isBlank() || IDENTIFIER_PATTERN.matcher(key).matches()) {
                    continue;
                }
                result.addAll(extractDefinitionNames(key));
            }
        }
        return result;
    }

    private static Set<String> extractDefinitionNames(String definitionKey) {
        var result = new HashSet<String>();
        try {
            var tokens = new TemplateLexer(definitionKey).tokens();
            for (int i = 0; i < tokens.size(); i++) {
                var token = tokens.get(i);
                if (token.type != TokenType.IDENT) {
                    continue;
                }
                var next = findNextNonTextToken(tokens, i + 1);
                if (next == null || next.type() != TokenType.KEYWORD) {
                    continue;
                }
                if (Keyword.RANGE.eq(next.token.lexeme) || Keyword.SWITCH.eq(next.token.lexeme)) {
                    result.add(token.lexeme);
                }
            }
        } catch (Exception ignored) {
            // Ignore malformed definition key and keep best-effort context.
        }
        return result;
    }

    private static Set<String> extractGlobalDefinitions(@NotNull JsonNode scriptNode) {
        var globals = new HashSet<String>();
        collectGlobalDefinitionsFromNode(scriptNode.get("globals"), globals);
        collectGlobalDefinitionsFromNode(scriptNode.get("global"), globals);
        return globals;
    }

    private static void collectGlobalDefinitionsFromNode(JsonNode node, @NotNull Set<String> globals) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isTextual()) {
            var name = node.asText();
            if (IDENTIFIER_PATTERN.matcher(name).matches()) {
                globals.add(name);
            }
            return;
        }
        if (node.isArray()) {
            for (var item : node) {
                collectGlobalDefinitionsFromNode(item, globals);
            }
            return;
        }
        if (node.isObject()) {
            var fields = node.fieldNames();
            while (fields.hasNext()) {
                var key = fields.next();
                if (IDENTIFIER_PATTERN.matcher(key).matches()) {
                    globals.add(key);
                }
            }
        }
    }

    private static boolean isExternalRootVariable(@NotNull List<Token> tokens,
                                                  int identIndex,
                                                  @NotNull Set<String> localDefinitions,
                                                  @NotNull Set<String> rangeBindings) {
        var token = tokens.get(identIndex);
        if (token.type != TokenType.IDENT || localDefinitions.contains(token.lexeme)) {
            return false;
        }
        var previous = findPreviousNonTextToken(tokens, identIndex - 1);
        if (previous == null || previous.type() != TokenType.DOT) {
            return false;
        }
        var beforeDot = findPreviousNonTextToken(tokens, previous.index - 1);
        if (beforeDot != null && beforeDot.type() == TokenType.IDENT) {
            return false;
        }
        return !rangeBindings.contains(token.lexeme);
    }

    private static String[] readPathSegments(@NotNull List<Token> tokens, int rootIndex) {
        var segments = new java.util.ArrayList<String>();
        segments.add(tokens.get(rootIndex).lexeme);
        var cursor = rootIndex;
        while (true) {
            var dot = findNextNonTextToken(tokens, cursor + 1);
            if (dot == null || dot.type() != TokenType.DOT) {
                break;
            }
            var segment = findNextNonTextToken(tokens, dot.index + 1);
            if (segment == null || segment.type() != TokenType.IDENT) {
                break;
            }
            segments.add(segment.token.lexeme);
            cursor = segment.index;
        }
        return segments.toArray(String[]::new);
    }

    private static Set<String> collectRangeBindings(@NotNull List<Token> tokens) {
        var bindings = new HashSet<String>();
        for (int i = 0; i < tokens.size(); i++) {
            var token = tokens.get(i);
            if (token.type != TokenType.KEYWORD || !Keyword.RANGE.eq(token.lexeme)) {
                continue;
            }
            collectRangeBindingsForKeyword(tokens, i, bindings);
        }
        return bindings;
    }

    private static void collectRangeBindingsForKeyword(@NotNull List<Token> tokens,
                                                       int rangeKeywordIndex,
                                                       @NotNull Set<String> bindings) {
        var firstBinding = findNextNonTextToken(tokens, rangeKeywordIndex + 1);
        if (firstBinding == null || firstBinding.type() != TokenType.IDENT) {
            bindings.add("item");
            bindings.add("index");
            return;
        }
        bindings.add(firstBinding.token.lexeme);

        var maybeComma = findNextNonTextToken(tokens, firstBinding.index + 1);
        if (maybeComma == null || maybeComma.type() != TokenType.COMMA) {
            return;
        }
        var secondBinding = findNextNonTextToken(tokens, maybeComma.index + 1);
        if (secondBinding != null && secondBinding.type() == TokenType.IDENT) {
            bindings.add(secondBinding.token.lexeme);
        }
    }

    private static IndexedToken findPreviousNonTextToken(@NotNull List<Token> tokens, int from) {
        for (int i = from; i >= 0; i--) {
            if (tokens.get(i).type != TokenType.TEXT) {
                return new IndexedToken(i, tokens.get(i));
            }
        }
        return null;
    }

    private static IndexedToken findNextNonTextToken(@NotNull List<Token> tokens, int from) {
        for (int i = from; i < tokens.size(); i++) {
            if (tokens.get(i).type != TokenType.TEXT) {
                return new IndexedToken(i, tokens.get(i));
            }
        }
        return null;
    }

    private static void addPath(@NotNull Map<String, Object> root, @NotNull String[] segments) {
        Map<String, Object> cursor = root;
        for (var i = 0; i < segments.length; i++) {
            var segment = segments[i];
            var leaf = i == segments.length - 1;
            var existing = cursor.get(segment);
            if (leaf) {
                if (!(existing instanceof Map<?, ?>)) {
                    cursor.put(segment, null);
                }
                return;
            }

            if (existing instanceof Map<?, ?> existingMap) {
                @SuppressWarnings("unchecked")
                var cast = (Map<String, Object>) existingMap;
                cursor = cast;
                continue;
            }

            var next = new LinkedHashMap<String, Object>();
            cursor.put(segment, next);
            cursor = next;
        }
    }

    private record IndexedToken(int index, Token token) {
        private TokenType type() {
            return token.type;
        }
    }

    private static void formatActiveFileJson(@NotNull Project project) {
        try {
            var sourceFile = getCurrentJjtemplateFile(project);
            if (sourceFile == null) {
                return;
            }
            var document = FileDocumentManager.getInstance().getDocument(sourceFile);
            if (document == null) {
                showError(project, "Unable to read current file.");
                return;
            }
            var parsed = MAPPER.readTree(document.getText());
            var pretty = createPrettyWriter(JjtemplateIndentOptions.getIndent(project)).writeValueAsString(parsed);
            WriteCommandAction.runWriteCommandAction(project, () -> document.setText(pretty));
        } catch (Exception exception) {
            showError(project, "Invalid JSON in active file:\n" + getRootMessage(exception));
        }
    }

    private static String formatContextJson(@NotNull Project project, @NotNull EditorTextField contextInput) {
        try {
            var parsed = MAPPER.readValue(contextInput.getText(), MAP_TYPE);
            var pretty = createPrettyWriter(JjtemplateIndentOptions.getIndent(project)).writeValueAsString(parsed);
            contextInput.setText(pretty);
            return pretty;
        } catch (Exception exception) {
            showError(project, "Invalid context JSON:\n" + getRootMessage(exception));
            return null;
        }
    }

    private static boolean isNotJjtemplateFile(@NotNull VirtualFile file) {
        if (file.getFileType() == JjtemplateFileType.INSTANCE) {
            return false;
        }
        var extension = file.getExtension();
        return !"jjt".equalsIgnoreCase(extension) && !"jjtemplate".equalsIgnoreCase(extension);
    }

    private static @NotNull ObjectWriter createPrettyWriter(int jsonIndent) {
        var indent = " ".repeat(JjtemplateIndentOptions.normalize(jsonIndent));
        var indenter = new DefaultIndenter(indent, DefaultIndenter.SYS_LF);
        var printer = new DefaultPrettyPrinter()
                .withObjectIndenter(indenter)
                .withArrayIndenter(indenter);
        return MAPPER.writer(printer);
    }

    private static void showError(@NotNull Project project, @NotNull String message) {
        Messages.showErrorDialog(project, message, "JJTemplate");
    }

    private static @NotNull String getRootMessage(@NotNull Throwable throwable) {
        var cursor = throwable;
        while (cursor.getCause() != null && cursor.getCause() != cursor) {
            cursor = cursor.getCause();
        }
        var message = cursor.getMessage();
        if (message == null || message.isBlank()) {
            message = throwable.getMessage();
        }
        return (message == null || message.isBlank()) ? cursor.getClass().getSimpleName() : message;
    }
}
