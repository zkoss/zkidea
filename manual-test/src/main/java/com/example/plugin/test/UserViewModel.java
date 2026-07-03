package com.example.plugin.test;

import org.zkoss.bind.annotation.Init;

/**
 * ViewModel for viewmodel-id-navigation tests.
 * Ctrl+Click on "vm" in @load(vm.userName) should navigate to this class.
 */
public class UserViewModel {

    private String userName = "Charlie";
    private User user = new User();

    @Init
    public void init() {}

    /** Supports: @load(vm.userName) */
    public String getUserName() { return userName; }

    /**
     * Supports: @load(vm.user.name)
     * Clicking "user" (non-root segment) must NOT navigate to UserViewModel.
     */
    public User getUser() { return user; }
}