package com.whisent.kubeloader.klm.dsl;

public class EventProbeDSL {
    // 事件名称，如'StartupEvents.init'
    private String eventName;
    // 注入位置，'head' 或 'tail'
    private String position;
    // 函数体内容
    private String functionBody;
    // 目标位置（第几个事件订阅）
    private int targetLocation = 0;

    public String getEventName() {
        return eventName;
    }

    public void setEventName(String eventName) {
        this.eventName = eventName;
    }

    public String getPosition() {
        return position;
    }

    public void setPosition(String position) {
        this.position = position;
    }

    public String getFunctionBody() {
        return functionBody;
    }

    public void setFunctionBody(String functionBody) {
        this.functionBody = functionBody;
    }

    public int getTargetLocation() {
        return targetLocation;
    }

    public void setTargetLocation(int targetLocation) {
        this.targetLocation = targetLocation;
    }

    @Override
    public String toString() {
        return "EventProbeDSL{" +
                "eventName='" + eventName + '\'' +
                ", position='" + position + '\'' +
                ", functionBody='" + functionBody + '\'' +
                ", targetLocation='" + targetLocation + '\'' +
                '}';
    }
}