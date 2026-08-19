package com.example.springbootjar;

/**
 * ViewModel behind {@code web/include-binding.zul} (manual test for issue #69).
 *
 * <p>Never instantiated by the Layout Preview: the preview's {@code UiFactory} hook hands every
 * ViewModel a no-op composer, so {@code @load(vm.popupSrc)} is deliberately left unresolved and
 * the include it feeds contributes nothing. This class exists so the sample page references a
 * real ViewModel, like the reported project does; in a running app it would supply the path.
 */
public class IncludeViewModel {

    public String getPopupSrc() {
        return "~./zul/pop-listbox.zul";
    }
}
