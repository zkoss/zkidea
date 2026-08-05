package org.zkoss.zkpreview.javax.mock;

import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Captures bytes written by ZK's response output stream into the buffer owned by
 * {@link org.zkoss.zkpreview.mockcore.MockHttpServletResponseCore} (review M1). Must stay
 * per-namespace: it {@code extends} the servlet {@code ServletOutputStream} abstract class, which
 * differs between the two servlet namespaces, so no shared base is possible.
 */
public class MockServletOutputStream extends ServletOutputStream {

    private final ByteArrayOutputStream buffer;

    public MockServletOutputStream(ByteArrayOutputStream buffer) {
        this.buffer = buffer;
    }

    @Override
    public boolean isReady() {
        return true;
    }

    @Override
    public void setWriteListener(WriteListener writeListener) {
    }

    @Override
    public void write(int b) throws IOException {
        buffer.write(b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        buffer.write(b, off, len);
    }
}
