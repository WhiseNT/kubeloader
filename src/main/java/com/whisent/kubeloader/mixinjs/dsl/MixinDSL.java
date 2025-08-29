package com.whisent.kubeloader.mixinjs.dsl;

public class MixinDSL {
    private String sourcePath;
    //目标的文件
    private String targetFile;
    //
    private String targetLocation;
    //注入的类型
    private String type;
    //注入位置
    private String at;
    //注入的代码
    private String actionCode;
    //注入对象的名称,如函数名
    private String target;

    public String getSourcePath() {
        return sourcePath;
    }

    public String getTargetFile() {
        return targetFile;
    }

    public void setTargetFile(String targetFile) {
        this.targetFile = targetFile;
    }

    public String getTargetLocation() {
        return targetLocation;
    }

    public void setTargetLocation(String targetLocation) {
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

    public void setAction(String action) {
        this.actionCode = action;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public void setSourcePath(String sourcePath) {
        this.sourcePath = sourcePath;
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
                '}';
    }
}