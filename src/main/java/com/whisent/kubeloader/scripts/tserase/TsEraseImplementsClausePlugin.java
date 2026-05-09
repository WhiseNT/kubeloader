package com.whisent.kubeloader.scripts.tserase;

final class TsEraseImplementsClausePlugin implements TsErasePlugin {
    @Override
    public String syntax() {
        return "class Foo implements Bar";
    }

    @Override
    public boolean matches(TsEraseContext context) {
        return context.atWord("implements");
    }

    @Override
    public boolean apply(TsEraseContext context) {
        int pos = context.position();
        while (pos < context.length() && context.charAt(pos) != '{') {
            pos++;
        }
        context.position(pos);
        return true;
    }
}
