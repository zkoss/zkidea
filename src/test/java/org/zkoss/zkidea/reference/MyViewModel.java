package org.zkoss.zkidea.reference;

import org.zkoss.bind.annotation.Command;
import org.zkoss.bind.annotation.GlobalCommand;

import java.util.List;

/**
 * Canonical ViewModel fixture for completion and navigation tests.
 *
 * <p>Getter-backed properties:
 * <ul>
 *   <li>{@code list}         — {@code getList()}         : List&lt;String&gt;</li>
 *   <li>{@code name}         — {@code getName()}         : String</li>
 *   <li>{@code active}       — {@code isActive()}        : boolean</li>
 *   <li>{@code crew}         — {@code getCrew()}         : Object (represents CrewModel)</li>
 *   <li>{@code selectedItem} — {@code getSelectedItem()} : Object</li>
 *   <li>{@code value}        — {@code getValue()}        : String</li>
 * </ul>
 *
 * <p>Non-getter public methods (also visible in property context):
 * <ul>
 *   <li>{@code init()}                     — 0 params</li>
 *   <li>{@code saveItem()}                 — 0 params, {@code @Command}</li>
 *   <li>{@code persistItem()}              — 0 params, {@code @Command("save")}</li>
 *   <li>{@code broadcast()}                — 0 params, {@code @GlobalCommand}</li>
 *   <li>{@code validate()}, {@code commit()}, {@code hello()} — 0 params, {@code @Command}</li>
 *   <li>{@code setValue(String)}           — 1 param</li>
 * </ul>
 *
 * <p>Edge-case members for filtering tests:
 * <ul>
 *   <li>{@code getFiltered(String)} — parameterised getter; excluded from all completion</li>
 *   <li>{@code protectedHelper()}   — non-public; excluded from all completion</li>
 * </ul>
 */
public class MyViewModel {

    // Getter-backed properties
    public List<String> getList()         { return null; }
    public String       getName()         { return null; }
    public boolean      isActive()        { return false; }
    public Object       getCrew()         { return null; }
    public Object       getSelectedItem() { return null; }
    public String       getValue()        { return null; }

    // Parameterised getter — must NOT appear in property completion (Pass 1 requires 0 params)
    public List<String> getFiltered(String query) { return null; }

    // Non-getter public methods
    public void init() {}

    @Command
    public void saveItem() {}

    @Command("save")
    public void persistItem() {}

    @GlobalCommand
    public void broadcast() {}

    @Command
    public void validate() {}

    @Command
    public void commit() {}

    @Command
    public void hello() {}

    public void setValue(String v) {}

    // Non-public method — must NOT appear in completion
    protected void protectedHelper() {}
}
