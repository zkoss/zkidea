package org.zkoss.zkpreview.jakarta.mock;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;

import org.zkoss.zkpreview.mockcore.MockHttpServletResponseCore;

/**
 * Jakarta {@link HttpServletResponse} adapter over {@link MockHttpServletResponseCore} (review M1,
 * Bridge pattern): status, headers and the captured body are inherited from the core; this class
 * supplies only the servlet-typed {@link #getOutputStream()} (a {@link MockServletOutputStream}
 * writing into the core's byte buffer) and the {@code addCookie} no-op.
 */
public class MockHttpServletResponse extends MockHttpServletResponseCore implements HttpServletResponse {

    private final MockServletOutputStream outputStream = new MockServletOutputStream(byteBuffer());

    @Override
    public ServletOutputStream getOutputStream() {
        return outputStream;
    }

    @Override
    public void addCookie(Cookie cookie) {
    }
}
