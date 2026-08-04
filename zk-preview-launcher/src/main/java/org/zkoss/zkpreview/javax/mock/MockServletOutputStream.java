package org.zkoss.zkpreview.javax.mock;

import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/** Captures bytes written by ZK's response output stream. */
public class MockServletOutputStream extends ServletOutputStream {

    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

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

    public ByteArrayOutputStream getBuffer() {
        return buffer;
    }
}
