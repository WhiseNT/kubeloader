package com.whisent.kubeloader.scripts.tserase;

final class TsEraseProcessor {
    private static final TsErasePluginManager PLUGINS = TsErasePluginManager.INSTANCE;

    private TsEraseProcessor() {
    }

    static String erase(String src) {
        TsEraseContext context = new TsEraseContext(src);

        while (context.hasCurrent()) {
            char current = context.currentChar();

            context.updateState(current);
            if (context.inCommentOrString()) {
                context.append(current);
                context.advance();
                continue;
            }

            if (PLUGINS.applyCurrent(context)) {
                continue;
            }

            context.append(current);
            context.advance();
        }

        return context.output();
    }
}
