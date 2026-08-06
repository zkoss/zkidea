package org.zkoss.zkpreview.testutil;

import java.util.List;
import java.util.stream.Stream;

/**
 * The ZK add-on × ZK core tuples the preview is verified against.
 *
 * <p>Add-ons are the population most likely to break the preview: they register
 * {@code WebAppInit} listeners, ship their own {@code lang-addon.xml} plus WPD/JS/CSS inside
 * their jars, and probe for licenses -- three things the mock container fakes. The zkcharts
 * NPE (a null {@code WebApp} attribute killed the launcher before it bound a port) reached a
 * user precisely because no add-on was exercised anywhere in this suite.
 *
 * <p>Versions are reviewed constants, never queried live, so a passing suite never depends on
 * what got published this morning. Each row pins <em>its own</em> ZK core -- that is required
 * for Keikai (6.3.0 needs ZK 10.3.0.1, 5.12.0 needs ZK 9.6.2) and it is also what makes the
 * single-jar add-ons meaningful: Charts, Calendar and Pivottable ship one jar for both servlet
 * variants, so each is crossed with a javax and a jakarta core, and the pairing that is older
 * or newer than the add-on's own target ZK is the interesting one.
 *
 * <p>Keikai is {@code io.keikai:keikai-ex}, not {@code io.keikai:keikai}: the latter's
 * {@code lang-addon.xml} declares no components at all, so {@code <spreadsheet>} is unknown.
 */
public final class AddonMatrix {

    private AddonMatrix() {
    }

    public static final class Row {
        public final String id;
        public final String addonGroupId;
        public final String addonArtifactId;
        public final String addonVersion;
        public final String zkVersion;
        public final boolean jakarta;
        /** Docroot-relative fixture that instantiates the add-on's headline component. */
        public final String fixture;
        /** Widget class the component must render as, e.g. {@code chart.Charts}. */
        public final String widgetClass;
        /**
         * Dotted <em>widget</em> package the add-on's JS module is served under, i.e.
         * {@code /zkau/web/<build-hash>/js/<widgetPackage>.wpd}. It comes from the widget
         * class -- NOT from {@code <javascript-module name>} and NOT from the jar's directory
         * layout: {@code calendar.wpd} serves 200 while the module name
         * {@code calendar.calendars.wpd} is a 404.
         */
        public final String widgetPackage;

        private Row(String id, String addonGroupId, String addonArtifactId, String addonVersion,
                String zkVersion, boolean jakarta, String fixture, String widgetClass,
                String widgetPackage) {
            this.id = id;
            this.addonGroupId = addonGroupId;
            this.addonArtifactId = addonArtifactId;
            this.addonVersion = addonVersion;
            this.zkVersion = zkVersion;
            this.jakarta = jakarta;
            this.fixture = fixture;
            this.widgetClass = widgetClass;
            this.widgetPackage = widgetPackage;
        }

        public ZkClasspathResolver.Resolution resolve() {
            return ZkClasspathResolver.resolve(pom());
        }

        String pom() {
            return POM_TEMPLATE
                    .replace("@ARTIFACT@", id)
                    .replace("@ZK@", zkVersion)
                    .replace("@ADDON_GROUP@", addonGroupId)
                    .replace("@ADDON_ARTIFACT@", addonArtifactId)
                    .replace("@ADDON_VERSION@", addonVersion)
                    .replace("@SERVLET_API@", jakarta ? JAKARTA_SERVLET_API : JAVAX_SERVLET_API);
        }

        @Override
        public String toString() {
            return id;
        }
    }

    private static final List<Row> ROWS = List.of(
            new Row("charts-12.5.0.0-zk9.6.0.2-javax", "org.zkoss.chart", "zkcharts", "12.5.0.0",
                    "9.6.0.2", false, "/addons/charts.zul", "chart.Charts", "chart"),
            new Row("charts-12.5.0.0-zk10.1.0-jakarta", "org.zkoss.chart", "zkcharts", "12.5.0.0",
                    "10.1.0-jakarta", true, "/addons/charts.zul", "chart.Charts", "chart"),
            new Row("calendar-3.2.1-zk9.6.0.2-javax", "org.zkoss.calendar", "calendar", "3.2.1",
                    "9.6.0.2", false, "/addons/calendar.zul", "calendar.CalendarsDefault", "calendar"),
            new Row("calendar-3.2.1-zk10.1.0-jakarta", "org.zkoss.calendar", "calendar", "3.2.1",
                    "10.1.0-jakarta", true, "/addons/calendar.zul", "calendar.CalendarsDefault", "calendar"),
            new Row("pivottable-3.1.0-Eval-zk10.1.0-javax", "org.zkoss.pivot", "pivottable", "3.1.0-Eval",
                    "10.1.0", false, "/addons/pivottable.zul", "pivot.Pivottable", "pivot"),
            new Row("pivottable-3.1.0-Eval-zk10.1.0-jakarta", "org.zkoss.pivot", "pivottable", "3.1.0-Eval",
                    "10.1.0-jakarta", true, "/addons/pivottable.zul", "pivot.Pivottable", "pivot"),
            new Row("keikai-ex-6.3.0-zk10.3.0.1-javax", "io.keikai", "keikai-ex", "6.3.0",
                    "10.3.0.1", false, "/addons/keikai.zul", "zssex.Spreadsheet", "zssex"),
            new Row("keikai-ex-6.3.0-jakarta-zk10.3.0.1-jakarta", "io.keikai", "keikai-ex", "6.3.0-jakarta",
                    "10.3.0.1-jakarta", true, "/addons/keikai.zul", "zssex.Spreadsheet", "zssex"),
            // Keikai's jakarta line starts at 5.13.0, so 5.12.0 is javax-only.
            new Row("keikai-ex-5.12.0-zk9.6.2-javax", "io.keikai", "keikai-ex", "5.12.0",
                    "9.6.2", false, "/addons/keikai.zul", "zssex.Spreadsheet", "zssex"),
            new Row("ckez-4.25.0.1-lts-zk9.6.0.2-javax", "org.zkoss.zkforge", "ckez", "4.25.0.1-lts",
                    "9.6.0.2", false, "/addons/ckeditor.zul", "ckez.CKeditor", "ckez"),
            new Row("ckez-4.25.0.1-lts-jakarta-zk10.1.0-jakarta", "org.zkoss.zkforge", "ckez",
                    "4.25.0.1-lts-jakarta", "10.1.0-jakarta", true, "/addons/ckeditor.zul",
                    "ckez.CKeditor", "ckez"));

    public static Stream<Row> rows() {
        return ROWS.stream();
    }

    private static final String JAVAX_SERVLET_API =
            "    <dependency>\n"
            + "      <groupId>javax.servlet</groupId>\n"
            + "      <artifactId>javax.servlet-api</artifactId>\n"
            + "      <version>4.0.1</version>\n"
            + "      <scope>provided</scope>\n"
            + "    </dependency>\n";

    private static final String JAKARTA_SERVLET_API =
            "    <dependency>\n"
            + "      <groupId>jakarta.servlet</groupId>\n"
            + "      <artifactId>jakarta.servlet-api</artifactId>\n"
            + "      <version>5.0.0</version>\n"
            + "      <scope>provided</scope>\n"
            + "    </dependency>\n";

    // Repository ids matter: EE and Keikai EE are credentialed, and Maven matches a <server>
    // in ~/.m2/settings.xml to a <repository> by id. A machine without those credentials
    // simply fails to resolve, and the row skips (see Resolution.skipReason).
    private static final String POM_TEMPLATE =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<project xmlns=\"http://maven.apache.org/POM/4.0.0\">\n"
            + "  <modelVersion>4.0.0</modelVersion>\n"
            + "  <groupId>org.zkoss.zkpreview</groupId>\n"
            + "  <artifactId>@ARTIFACT@</artifactId>\n"
            + "  <version>1.0-SNAPSHOT</version>\n"
            + "  <packaging>pom</packaging>\n"
            + "  <dependencies>\n"
            + "    <dependency>\n"
            + "      <groupId>org.zkoss.zk</groupId>\n"
            + "      <artifactId>zkbind</artifactId>\n"
            + "      <version>@ZK@</version>\n"
            + "    </dependency>\n"
            + "    <dependency>\n"
            + "      <groupId>@ADDON_GROUP@</groupId>\n"
            + "      <artifactId>@ADDON_ARTIFACT@</artifactId>\n"
            + "      <version>@ADDON_VERSION@</version>\n"
            + "    </dependency>\n"
            + "@SERVLET_API@"
            + "  </dependencies>\n"
            + "  <repositories>\n"
            + "    <repository><id>ZK CE</id><url>https://mavensync.zkoss.org/maven2</url></repository>\n"
            + "    <repository><id>ZK EE</id><url>https://maven.zkoss.org/repo/zk/ee</url></repository>\n"
            + "    <repository><id>ZK EE Eval</id><url>https://mavensync.zkoss.org/zk/ee-eval</url></repository>\n"
            + "    <repository><id>Keikai EE</id><url>https://maven.zkoss.org/repo/keikai/ee/</url></repository>\n"
            + "  </repositories>\n"
            + "</project>\n";
}
