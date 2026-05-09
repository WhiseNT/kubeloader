package com.whisent.kubeloader.scripts.modernjs;

import java.util.List;

final class ModernJSPluginManager {
    static final ModernJSPluginManager INSTANCE = new ModernJSPluginManager();

    private final List<SourcePlugin> sourcePlugins = List.of(
            new ClassBlockPlugin()
    );

    private final List<TextPlugin> postProcessPlugins = List.of(
            new ShorthandReturnObjectPlugin(),
            new DefaultParameterPlugin()
    );

    private ModernJSPluginManager() {
    }

    SourcePlugin findSourcePlugin(String trimmedLine) {
        for (SourcePlugin plugin : sourcePlugins) {
            if (plugin.matches(trimmedLine)) {
                return plugin;
            }
        }
        return null;
    }


    String applyPostProcessing(String input) {
        String result = input;
        for (TextPlugin plugin : postProcessPlugins) {
            result = plugin.apply(result);
        }
        return result;
    }
}
