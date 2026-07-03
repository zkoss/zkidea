package com.example.plugin.test;

import org.zkoss.bind.annotation.Init;

/**
 * Outer ViewModel for nested viewModel declaration tests.
 * viewModel="@id('outer') @init('com.example.plugin.test.OuterViewModel')"
 * Test: findViewModelTag must return the NEAREST ancestor — so attributes inside
 * the inner &lt;div&gt; must resolve to InnerViewModel, not this class.
 */
public class OuterViewModel {

    private String outerProperty = "outerValue";

    @Init
    public void init() {}

    public String getOuterProperty() { return outerProperty; }
}