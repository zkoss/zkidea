package org.zkoss.zkpreview.testcanary;

import java.util.List;

/**
 * Compiled as a normal test class (present on disk, on the test JVM's own classpath), with two
 * roles that depend on whether the render installs a {@code ForbiddenLoadTracker}.
 *
 * <p>With a tracker installed this class is kept off the classpath list a render's
 * {@code ScopedZkClassLoader} is built from -- the AC-4 negative control for the
 * {@code viewModel=} path. If isolation ever regresses, it would actually get constructed and
 * its value would leak into the rendered HTML.
 *
 * <p>With no tracker it is reachable on purpose, and it becomes the positive fixture for the
 * controllers-on column of the P0-2 placeholder matrix: the ViewModel that is <em>supposed</em>
 * to be constructed, so a controllers-on render has real bound values to assert against (see
 * {@link #getRows()}).
 */
public class CanaryViewModel {
    public static volatile boolean WAS_CONSTRUCTED = false;

    public CanaryViewModel() {
        WAS_CONSTRUCTED = true;
    }

    public String getGreeting() {
        return "LOADED-CANARY-VALUE";
    }

    public void doIt() {
    }

    /**
     * Real rows for {@code binding-model.zul}'s {@code model="@load(vm.rows)"}, so the
     * controllers-on column of the P0-2 placeholder matrix has something to assert against
     * (the isolated column feeds the same grid three synthetic placeholder rows instead).
     * Plain JDK types only: this sourceSet has no ZK compile dependency and must not gain one.
     */
    public List<Row> getRows() {
        return List.of(new Row("CANARY-ROW-A", "19.99"), new Row("CANARY-ROW-B", "29.99"));
    }

    /** The per-row bean {@code binding-model.zul}'s template reads as {@code each.name}/{@code each.price}. */
    public static final class Row {
        private final String name;
        private final String price;

        Row(String name, String price) {
            this.name = name;
            this.price = price;
        }

        public String getName() {
            return name;
        }

        public String getPrice() {
            return price;
        }
    }
}
