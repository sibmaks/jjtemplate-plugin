package io.github.sibmaks.jjtemplate.idea.docs;

import io.github.sibmaks.jjtemplate.compiler.runtime.fun.TemplateFunction;

import java.lang.reflect.Modifier;
import java.util.*;

public final class BuiltInFunctionIndex {
    private static final String BASE_PACKAGE = "io.github.sibmaks.jjtemplate.compiler.runtime.fun";
    private static final List<BuiltInFunctionInfo> FUNCTIONS = loadFunctions();
    private static final Map<String, BuiltInFunctionInfo> BY_LOOKUP = buildLookup(FUNCTIONS);

    private BuiltInFunctionIndex() {
    }

    public static List<BuiltInFunctionInfo> list() {
        return FUNCTIONS;
    }

    public static BuiltInFunctionInfo find(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        var direct = BY_LOOKUP.get(key);
        if (direct != null) {
            return direct;
        }
        return BY_LOOKUP.get(key.replace("::", ":"));
    }

    private static Map<String, BuiltInFunctionInfo> buildLookup(List<BuiltInFunctionInfo> functions) {
        var result = new HashMap<String, BuiltInFunctionInfo>();
        for (var function : functions) {
            result.putIfAbsent(function.lookupKey(), function);
            result.putIfAbsent(function.lookupKey().replace("::", ":"), function);
            result.putIfAbsent(function.name(), function);
        }
        return Collections.unmodifiableMap(result);
    }

    @SuppressWarnings("unchecked")
    private static List<BuiltInFunctionInfo> loadFunctions() {
        var result = new ArrayList<BuiltInFunctionInfo>();
        for (var type : findClasses(BASE_PACKAGE)) {
            if (!TemplateFunction.class.isAssignableFrom(type) || type.isInterface() || Modifier.isAbstract(type.getModifiers())) {
                continue;
            }
            try {
                var functionType = (Class<TemplateFunction<?>>) type;
                var constructor = functionType.getDeclaredConstructor();
                constructor.setAccessible(true);
                var function = constructor.newInstance();
                var namespace = Optional.ofNullable(function.getNamespace()).orElse("");
                var name = function.getName();
                var lookupKey = namespace.isEmpty() ? name : namespace + "::" + name;
                result.add(new BuiltInFunctionInfo(lookupKey, namespace, name, "Built-in JJTemplate function."));
            } catch (Throwable ignored) {
                // ignore non-instantiable classes
            }
        }
        result.sort(Comparator.comparing(BuiltInFunctionInfo::lookupKey));
        return Collections.unmodifiableList(result);
    }

    private static List<Class<?>> findClasses(String basePackage) {
        var classes = new ArrayList<Class<?>>();
        var path = basePackage.replace('.', '/');
        try {
            var resources = Thread.currentThread().getContextClassLoader().getResources(path);
            while (resources.hasMoreElements()) {
                var resource = resources.nextElement();
                if ("file".equals(resource.getProtocol())) {
                    var directory = new java.io.File(resource.toURI());
                    collectFromDirectory(directory, basePackage, classes);
                    continue;
                }
                if ("jar".equals(resource.getProtocol())) {
                    collectFromJar(resource.getPath(), path, classes);
                }
            }
        } catch (Exception ignored) {
            return classes;
        }
        return classes;
    }

    private static void collectFromJar(String resourcePath, String packagePath, List<Class<?>> classes) {
        var separator = resourcePath.indexOf("!");
        if (separator < 0) {
            return;
        }
        var jarPath = resourcePath.substring(5, separator);
        try (var jar = new java.util.jar.JarFile(jarPath)) {
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                var entryName = entry.getName();
                if (!entryName.startsWith(packagePath) || !entryName.endsWith(".class") || entryName.contains("$")) {
                    continue;
                }
                var className = entryName.substring(0, entryName.length() - 6).replace('/', '.');
                try {
                    classes.add(Class.forName(className));
                } catch (Throwable ignored) {
                    // ignore loading failures
                }
            }
        } catch (Exception ignored) {
            // ignore loading failures
        }
    }

    private static void collectFromDirectory(java.io.File directory, String basePackage, List<Class<?>> classes) {
        var files = directory.listFiles();
        if (files == null) {
            return;
        }
        for (var file : files) {
            if (file.isDirectory()) {
                collectFromDirectory(file, basePackage + "." + file.getName(), classes);
                continue;
            }
            var fileName = file.getName();
            if (!fileName.endsWith(".class") || fileName.contains("$")) {
                continue;
            }
            var className = basePackage + '.' + fileName.substring(0, fileName.length() - 6);
            try {
                classes.add(Class.forName(className));
            } catch (Throwable ignored) {
                // ignore loading failures
            }
        }
    }
}
