package com.example.plugin.test;

import org.zkoss.bind.annotation.Init;

/**
 * Concrete ViewModel that binds the generic type argument: {@code GenericVM<CrewModel>}.
 *
 * <p>Because of that binding, the inherited {@link GenericVM#getModel()} — whose declared
 * return type is the type variable {@code T} — effectively returns {@link CrewModel} here.
 * So {@code @load(vm.model.name)} must resolve {@code name} to {@link CrewModel#getName()}.
 *
 * <p>Used by {@code generic-inheritance-nav.zul}. Mirrors the automated test
 * {@code ViewModelGenericInheritanceResolutionTest} in the zkidea plugin.
 */
public class CrewVM extends GenericVM<CrewModel> {

    @Init
    public void init() {
        model = new CrewModel();
    }
}
