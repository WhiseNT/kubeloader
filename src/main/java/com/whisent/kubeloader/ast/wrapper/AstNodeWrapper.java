package com.whisent.kubeloader.ast.wrapper;

import dev.latvian.mods.kubejs.script.ScriptFile;
import dev.latvian.mods.rhino.Parser;
import dev.latvian.mods.rhino.ast.AstRoot;

public class AstNodeWrapper {
    public AstRoot root;
    public String[] source;
    public ScriptFile file;

    public AstRoot parent;
    public AstRoot[] children;

    public AstNodeWrapper(ScriptFile file) {
        this.file = file;
        this.source = file.info.lines;
        this.root = new Parser(file.pack.manager.context)
                .parse(String.join("\n", this.source),
                        this.file.info.file,0);
    }
    public AstNodeWrapper(ScriptFile file,AstRoot parent) {
        this( file);
        this.parent = parent;
        
    }
}
