package com.plantshop.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.encoder.Encoder;
import ch.qos.logback.core.encoder.EncoderBase;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Wraps any logback encoder (text pattern or structured JSON) and passes its output through
 * {@link PiiMasker} before it reaches the appender. Masking the rendered line rather than the
 * message covers every way PII gets into a log: format arguments, {@code toString()} of nested
 * DTOs, MDC, key-value pairs and exception messages anywhere in a cause chain.
 */
public class MaskingEncoder extends EncoderBase<ILoggingEvent> {

    private Encoder<ILoggingEvent> delegate;
    private Charset charset = StandardCharsets.UTF_8;

    public void setDelegate(Encoder<ILoggingEvent> delegate) {
        this.delegate = delegate;
    }

    /** Must match the charset of the delegate. */
    public void setCharset(Charset charset) {
        this.charset = charset;
    }

    @Override
    public void start() {
        if (delegate == null) {
            addError("No delegate encoder set for MaskingEncoder");
            return;
        }
        if (!delegate.isStarted()) {
            delegate.setContext(getContext());
            delegate.start();
        }
        super.start();
    }

    @Override
    public void stop() {
        delegate.stop();
        super.stop();
    }

    @Override
    public byte[] headerBytes() {
        return delegate.headerBytes();
    }

    @Override
    public byte[] encode(ILoggingEvent event) {
        byte[] rendered = delegate.encode(event);
        if (rendered == null || rendered.length == 0) {
            return rendered;
        }
        return PiiMasker.mask(new String(rendered, charset)).getBytes(charset);
    }

    @Override
    public byte[] footerBytes() {
        return delegate.footerBytes();
    }
}
