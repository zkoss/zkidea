package com.example.plugin.test;

import org.zkoss.bind.annotation.Command;
import org.zkoss.bind.annotation.GlobalCommand;
import org.zkoss.bind.annotation.Init;

import java.util.Arrays;
import java.util.List;

/**
 * Main ViewModel for plugin manual tests.
 * Covers: binding property navigation, command binding navigation, scope-var completion.
 */
public class MyViewModel {

    private List<String> list = Arrays.asList("alpha", "beta", "gamma");
    private String name = "Alice";
    private boolean active = true;
    private CrewModel crew = new CrewModel();
    private CrewModel selectedItem = new CrewModel();
    private String value = "hello";

    @Init
    public void init() {}

    // ── Properties ────────────────────────────────────────────────────────────

    /** Supports: @load(vm.list) */
    public List<String> getList() { return list; }

    /** Supports: @load(vm.name) */
    public String getName() { return name; }

    /** Supports: @load(vm.active)  →  isActive() boolean getter */
    public boolean isActive() { return active; }

    /** Supports: @load(vm.crew.name)  →  nested path navigation */
    public CrewModel getCrew() { return crew; }

    /** Supports: @load(vm.selectedItem)  in @command('delete', item=vm.selectedItem) */
    public CrewModel getSelectedItem() { return selectedItem; }

    /** Supports: @save(vm.value, before='validate', after='commit') */
    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }

    // ── Commands ──────────────────────────────────────────────────────────────

    /** Supports: @command('saveItem') */
    @Command
    public void saveItem() {}

    /**
     * Supports: @command('save')
     * Note: method name differs from command name — navigate via annotation value.
     */
    @Command(value = "save")
    public void persistItem() {}

    /** Supports: @global-command('broadcast') */
    @GlobalCommand
    public void broadcast() {}

    /** Supports: @save(vm.value, before='validate') */
    @Command
    public void validate() {}

    /** Supports: @save(vm.value, after='commit') */
    @Command
    public void commit() {}

    @Command
    public void hello(){
        System.out.println("Hello, ZK!");
    }
}