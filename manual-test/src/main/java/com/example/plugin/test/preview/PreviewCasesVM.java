package com.example.plugin.test.preview;

import org.zkoss.bind.annotation.Init;
import org.zkoss.zul.DefaultTreeModel;
import org.zkoss.zul.DefaultTreeNode;
import org.zkoss.zul.GroupsModel;
import org.zkoss.zul.GroupsModelArray;
import org.zkoss.zul.TreeNode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ViewModel backing the grid/listbox preview cases under
 * {@code src/main/webapp/preview/cases/}. Supplies real sample data so the
 * <b>Jetty</b> run renders actual rows (the baseline), while the isolated
 * <b>preview</b> renders placeholder rows for the same ZUL (this class is never
 * loaded by the preview).
 */
public class PreviewCasesVM {

    private final List<Product> products = new ArrayList<>();
    private final List<String> tags = Arrays.asList("new", "sale", "featured", "clearance");
    private String title = "Preview Cases";
    private Product selected;

    @Init
    public void init() {
        products.add(new Product(1, "Keyboard", "Peripherals", 29.90, 12, true));
        products.add(new Product(2, "Mouse", "Peripherals", 15.50, 40, true));
        products.add(new Product(3, "Monitor", "Displays", 189.00, 5, true));
        products.add(new Product(4, "Webcam", "Peripherals", 45.00, 0, false));
        products.add(new Product(5, "USB-C Hub", "Accessories", 33.75, 22, true));
        products.add(new Product(6, "Laptop Stand", "Accessories", 24.00, 8, true));
        products.add(new Product(7, "4K Monitor", "Displays", 349.00, 3, true));
        products.add(new Product(8, "Headset", "Peripherals", 59.90, 0, false));
        selected = products.get(2);
    }

    public List<Product> getProducts() { return products; }

    public List<String> getTags() { return tags; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public Product getSelected() { return selected; }
    public void setSelected(Product selected) { this.selected = selected; }

    /** Grid/listbox grouping (G4/L4): products grouped by category, with a head + foot. */
    public GroupsModel<Product, String, String> getProductGroups() {
        List<Product> sorted = new ArrayList<>(products);
        sorted.sort(Comparator.comparing(Product::getCategory));
        return new GroupsModelArray<Product, String, String, Product>(sorted.toArray(new Product[0]),
                Comparator.comparing(Product::getCategory)) {
            @Override
            protected String createGroupHead(Product[] groupdata, int index, int col) {
                return groupdata[0].getCategory();
            }

            @Override
            protected String createGroupFoot(Product[] groupdata, int index, int col) {
                return groupdata.length + " item(s)";
            }
        };
    }

    /** Tree grouping (T1): products under category branches (all nodes are Products so
     * the template's @load(node.*) bindings stay typed). Root is not rendered. */
    public DefaultTreeModel<Product> getTreeModel() {
        Map<String, List<Product>> byCat = new LinkedHashMap<>();
        for (Product p : products) {
            byCat.computeIfAbsent(p.getCategory(), k -> new ArrayList<>()).add(p);
        }
        List<TreeNode<Product>> categories = new ArrayList<>();
        for (Map.Entry<String, List<Product>> e : byCat.entrySet()) {
            List<TreeNode<Product>> leaves = new ArrayList<>();
            for (Product p : e.getValue()) {
                leaves.add(new DefaultTreeNode<>(p));
            }
            categories.add(new DefaultTreeNode<>(new Product(0, e.getKey(), e.getKey(), 0, 0, true), leaves));
        }
        DefaultTreeNode<Product> root =
                new DefaultTreeNode<>(new Product(0, "Catalog", "", 0, 0, true), categories);
        return new DefaultTreeModel<>(root);
    }
}
