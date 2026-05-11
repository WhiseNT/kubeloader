package com.whisent.kubeloader.scripts.modernjs;



import com.whisent.kubeloader.scripts.modernjs.plugin.*;
import com.whisent.kubeloader.scripts.modernjs.plugin.impl.ClassMemberPlugin;
import com.whisent.kubeloader.scripts.modernjs.plugin.impl.SourcePlugin;
import com.whisent.kubeloader.scripts.modernjs.plugin.impl.TextPlugin;

import java.util.List;

public class ModernJSPluginManager {
    public static final ModernJSPluginManager INSTANCE = new ModernJSPluginManager();

    private final List<SourcePlugin> sourcePlugins = List.of(
            new ClassBlockPlugin()
    );

        private final List<ClassMemberPlugin> classMemberPlugins = List.of(
            new StaticMemberPlugin(),
            new AccessorPlugin(),
            new MethodPlugin(),
            new AssignedFieldPlugin(),
            new PlainFieldPlugin()
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

    public ClassMemberPlugin findClassMemberPlugin(String statement) {
        for (ClassMemberPlugin plugin : classMemberPlugins) {
            if (plugin.matches(statement)) {
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
