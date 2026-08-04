package org.zkoss.zkpreview.testutil;

import java.util.stream.Stream;

/** Shared {@code @MethodSource} for parametrizing tests over both servlet-API variants. */
public final class Variants {

    private Variants() {
    }

    public static Stream<Named> both() {
        return Stream.of(
                new Named("jakarta (ZK 10.1.0-jakarta)", ZkClasspathResolver::resolveJakarta),
                new Named("javax (ZK CE 9.6.0.2)", ZkClasspathResolver::resolveJavax));
    }

    public static final class Named {
        private final String label;
        private final java.util.function.Supplier<ZkClasspathResolver.Resolution> resolver;

        Named(String label, java.util.function.Supplier<ZkClasspathResolver.Resolution> resolver) {
            this.label = label;
            this.resolver = resolver;
        }

        public ZkClasspathResolver.Resolution resolve() {
            return resolver.get();
        }

        @Override
        public String toString() {
            return label;
        }
    }
}
