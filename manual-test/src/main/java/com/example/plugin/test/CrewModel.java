package com.example.plugin.test;

/**
 * Nested model for testing multi-segment property navigation.
 * Supports: @load(vm.crew.name)  — second-segment navigation to getName()
 */
public class CrewModel {

    private String name = "Bob";

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}