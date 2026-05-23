package com.whisent.kubeloader.graal.probe.jsdoc;

import java.util.ArrayList;
import java.util.List;

public final class JsDocMetadata {
    private String description;
    private String type;
    private String typedefName;
    private String typedefType;
    private String callbackName;
    private String returnsType;
    private String returnsDescription;
    private boolean readonly;
    private final List<JsDocParam> params = new ArrayList<>();
    private final List<JsDocProperty> properties = new ArrayList<>();

    public String description() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String type() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String typedefName() {
        return typedefName;
    }

    public void setTypedefName(String typedefName) {
        this.typedefName = typedefName;
    }

    public String typedefType() {
        return typedefType;
    }

    public void setTypedefType(String typedefType) {
        this.typedefType = typedefType;
    }

    public String callbackName() {
        return callbackName;
    }

    public void setCallbackName(String callbackName) {
        this.callbackName = callbackName;
    }

    public String returnsType() {
        return returnsType;
    }

    public void setReturnsType(String returnsType) {
        this.returnsType = returnsType;
    }

    public String returnsDescription() {
        return returnsDescription;
    }

    public void setReturnsDescription(String returnsDescription) {
        this.returnsDescription = returnsDescription;
    }

    public boolean readonly() {
        return readonly;
    }

    public void setReadonly(boolean readonly) {
        this.readonly = readonly;
    }

    public List<JsDocParam> params() {
        return params;
    }

    public void addParam(JsDocParam param) {
        params.add(param);
    }

    public List<JsDocProperty> properties() {
        return properties;
    }

    public void addProperty(JsDocProperty property) {
        properties.add(property);
    }

    public boolean isEmpty() {
        return (description == null || description.isBlank())
                && (type == null || type.isBlank())
                && (typedefName == null || typedefName.isBlank())
                && (typedefType == null || typedefType.isBlank())
                && (callbackName == null || callbackName.isBlank())
                && (returnsType == null || returnsType.isBlank())
                && (returnsDescription == null || returnsDescription.isBlank())
                && !readonly
                && params.isEmpty()
                && properties.isEmpty();
    }

    public record JsDocParam(String name, String type, String description, boolean optional, boolean rest,
                             String defaultValue) {
    }

    public record JsDocProperty(String name, String type, String description, boolean optional, boolean readonly) {
    }
}