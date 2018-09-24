package uk.ac.rdg.resc.ncwms.servlet;

import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import java.io.IOException;

/**
 * Prevent writes to the wrapped ServletOutputStream once it has thrown an exception.
 * This works around an obscure issue with tomcat and ImageFormat.writeImage where
 * if the ServletOutputStream is passed as the outputstream it will be flushed by
 * a finalizer at some random time if an exception is thrown
 * (in our case a ClientAbortException). This can interfere with the processing of subsequent
 * requests as tomcat reuses the ServletOutputStream.
 * Wrap a ServletOutputStream with an instance of this class and pass to the ImageFormat.imageWriter
 * to prevent the finalizer from interfering with the ServletOutputStream after the exception has
 * already been handled.
 */

public class ServletOutputStreamWrapper extends ServletOutputStream {
    private final ServletOutputStream outputStream;
    private boolean invalidated = false;

    public ServletOutputStreamWrapper(ServletOutputStream outputStream) {
        this.outputStream = outputStream;
    }

    @Override
    public void write(int i) throws IOException {
        if (invalidated) return;

        try {
            outputStream.write(i);
        } catch (IOException ioe) {
            invalidated = true;
            throw ioe;
        }
    }

    @Override
    public boolean isReady() {
        return outputStream.isReady();
    }

    @Override
    public void setWriteListener(WriteListener writeListener) {
        outputStream.setWriteListener(writeListener);
    }

    @Override
    public void flush() throws IOException {
        if (invalidated) return;

        try {
            outputStream.flush();
        } catch (IOException ioe) {
            invalidated = true;
            throw ioe;
        }
    }

    @Override
    public void close() throws IOException {
        outputStream.close();
    }

}
