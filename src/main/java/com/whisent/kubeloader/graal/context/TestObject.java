package com.whisent.kubeloader.graal.context;

public class TestObject {
    public TestObject() {
        System.out.println("TestObject created");
    }
    public void printMessage() {
        TestEvent.listeners.forEach(consumer -> {
            consumer.accept("Test message");
        });
    }
}
