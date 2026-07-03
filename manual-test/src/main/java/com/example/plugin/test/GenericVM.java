package com.example.plugin.test;

/**
 * Generic base ViewModel for testing binding-chain resolution past an inherited
 * generic getter (PR #62 / issue #61).
 *
 * <p>The getter {@link #getModel()} is declared here and its return type is the bare
 * type variable {@code T}. The concrete subclass binds {@code T} to a real class
 * (see {@link CrewVM} {@code extends GenericVM<CrewModel>}), so the plugin must
 * substitute {@code T} with that argument to resolve a chain such as
 * {@code @load(vm.model.name)} past the {@code model} segment.
 */
public abstract class GenericVM<T> {

    protected T model;

    /**
     * Getter declared on the generic base — return type is the type variable {@code T}.
     * Before the fix the chain walker read this as the raw {@code T}, could not resolve
     * it to a class, and marked everything after {@code model} as unresolved (red).
     */
    public T getModel() { return model; }
}
