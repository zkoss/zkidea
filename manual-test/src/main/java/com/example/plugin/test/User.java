package com.example.plugin.test;

/**
 * Nested model for viewmodel-id-navigation: @load(vm.user.name).
 * "user" is the second chain segment — navigates to User class, not UserViewModel.
 */
public class User {

    private String name = "Dave";

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}