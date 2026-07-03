package com.example.plugin.test;

import org.zkoss.bind.annotation.Init;

/**
 * Inner ViewModel for nested viewModel declaration tests.
 * viewModel="@id('inner') @init('com.example.plugin.test.InnerViewModel')"
 * Attributes inside this scope navigate to InnerViewModel properties, not OuterViewModel.
 */
public class InnerViewModel {

    private String innerProperty = "innerValue";

    @Init
    public void init() {}

    public String getInnerProperty() { return innerProperty; }
}