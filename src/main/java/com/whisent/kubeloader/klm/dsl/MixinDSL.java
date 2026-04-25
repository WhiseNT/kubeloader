package com.whisent.kubeloader.klm.dsl;

import dev.latvian.mods.kubejs.script.ScriptFile;
import dev.latvian.mods.kubejs.typings.Info;

public class MixinDSL {
    private ScriptFile file;

    private String sourcePath;
    //目标文件路径
    private String targetFile;
    //具体的位置（如多个事件订阅,用数字来定位第几个事件)
    private int targetLocation = 0;
    //注入的类型（函数声明、事件订阅）
    private String type;
    //注入位置(头、尾)
    private String at;
    //注入的代码
    private String actionCode;
    //注入目标的名称,如函数名
    private String target;
    //定位表达式（新版: function:myFunc, call:console.log）
    private String locatorExpression;
    //行偏移量（在函数体内第几行注入，从0开始）
    private int offset = -1;
    //优先级，优先级越高的MixinDSL越先应用
    private int priority = 0;

    public String getSourcePath() {
        return sourcePath;
    }

    public String getTargetFile() {
        return targetFile;
    }

    public void setTargetFile(String targetFile) {
        this.targetFile = targetFile;
    }

    public int getTargetLocation() {
        return targetLocation;
    }

    public void setTargetLocation(int targetLocation) {
        this.targetLocation = targetLocation;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getAt() {
        return at;
    }

    public void setAt(String at) {
        this.at = at;
    }

    public String getAction() {
        return actionCode;
    }
    public int getPriority() {
        return priority;
    }

    public void setAction(String action) {
        this.actionCode = action;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
        this.locatorExpression = target;
    }

    public void setSourcePath(String sourcePath) {
        this.sourcePath = sourcePath;
    }
    public void setPriority(int priority) {
        this.priority = priority;
    }
    public ScriptFile getFile() {
        return file;
    }
    public void setFile(ScriptFile file) {
        this.file = file;
    }

    /**
     * 设置定位表达式（新版 API）
     * @param expression 如 function:myFunc, call:console.log
     */
    public void setLocatorExpression(String expression) {
        this.locatorExpression = expression;
    }

    /**
     * 获取定位表达式字符串
     */
    public String getLocatorExpression() {
        return locatorExpression != null ? locatorExpression : target;
    }

    /**
     * 获取行偏移量（-1表示未设置，使用head/tail）
     */
    public int getOffset() {
        return offset;
    }

    /**
     * 设置行偏移量
     * @param offset 在函数体内第几行注入（从0开始）
     */
    public void setOffset(int offset) {
        this.offset = offset;
    }

    @Override
    public String toString() {
        return "MixinDSL{" +
                "sourcePath='" + sourcePath + '\'' +
                ", targetFile='" + targetFile + '\'' +
                ", targetLocation='" + targetLocation + '\'' +
                ", type='" + type + '\'' +
                ", at='" + at + '\'' +
                ", actionCode='" + actionCode + '\'' +
                ", target='" + target + '\'' +
                ", locatorExpression='" + locatorExpression + '\'' +
                ", offset=" + offset +
                ", priority='" + priority + '\'' +
                '}';
    }
}
