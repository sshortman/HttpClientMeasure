package com.santaba.agent.http;

import org.apache.http.annotation.NotThreadSafe;
import org.apache.http.impl.conn.Wire;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Internal class.
 *
 * @since 4.3
 */
@NotThreadSafe
class LoggingOutputStream extends OutputStream {

    private final OutputStream out;
    private final Wire wire;

    public LoggingOutputStream(final OutputStream out, final Wire wire) {
        super();
        this.out = out;
        this.wire = wire;
    }

    @Override
    public void write(final int b) throws IOException {
        try {
            wire.output(b);
        } catch (IOException ex) {
            wire.output("[write] I/O error: " + ex.getMessage());
            throw ex;
        }
    }

    @Override
    public void write(final byte[] b) throws IOException {
        try {
            wire.output(b);
            out.write(b);
        } catch (IOException ex) {
            wire.output("[write] I/O error: " + ex.getMessage());
            throw ex;
        }
    }

    @Override
    public void write(final byte[] b, final int off, final int len) throws IOException {
        try {
            wire.output(b, off, len);
            out.write(b, off, len);
        } catch (IOException ex) {
            wire.output("[write] I/O error: " + ex.getMessage());
            throw ex;
        }
    }

    @Override
    public void flush() throws IOException {
        try {
            out.flush();
        } catch (IOException ex) {
            wire.output("[flush] I/O error: " + ex.getMessage());
            throw ex;
        }
    }

    @Override
    public void close() throws IOException {
        try {
            out.close();
        } catch (IOException ex) {
            wire.output("[close] I/O error: " + ex.getMessage());
            throw ex;
        }
    }

}
