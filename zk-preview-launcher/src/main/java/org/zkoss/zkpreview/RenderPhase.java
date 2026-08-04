package org.zkoss.zkpreview;

/** Which stage of the render pipeline a {@link RenderError} originated from. */
public enum RenderPhase {
    CLASSPATH,
    PARSE,
    COMPOSE,
    RESOURCE,
    UNKNOWN
}
