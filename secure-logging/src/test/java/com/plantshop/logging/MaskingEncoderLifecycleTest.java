package com.plantshop.logging;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.encoder.EncoderBase;
import ch.qos.logback.core.status.Status;
import org.junit.jupiter.api.Test;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

/** The wrapper must behave exactly like the encoder it wraps, apart from masking. */
class MaskingEncoderLifecycleTest {

    private final LoggerContext context = new LoggerContext();

    /** Returns fixed header/footer and renders the event message as is. */
    static class FixedEncoder extends EncoderBase<ILoggingEvent> {
        @Override
        public byte[] headerBytes() {
            return "HEADER".getBytes(UTF_8);
        }

        @Override
        public byte[] encode(ILoggingEvent event) {
            return event.getMessage().getBytes(UTF_8);
        }

        @Override
        public byte[] footerBytes() {
            return "FOOTER".getBytes(UTF_8);
        }
    }

    @Test
    void startsAndStopsTheDelegateWithItsOwnContext() {
        FixedEncoder delegate = new FixedEncoder();
        MaskingEncoder encoder = encoder(delegate);

        encoder.start();
        assertThat(encoder.isStarted()).isTrue();
        assertThat(delegate.isStarted()).isTrue();
        assertThat(delegate.getContext()).isSameAs(context);

        encoder.stop();
        assertThat(encoder.isStarted()).isFalse();
        assertThat(delegate.isStarted()).isFalse();
    }

    @Test
    void doesNotRestartADelegateThatIsAlreadyRunning() {
        FixedEncoder delegate = new FixedEncoder();
        LoggerContext other = new LoggerContext();
        delegate.setContext(other);
        delegate.start();

        encoder(delegate).start();

        assertThat(delegate.getContext()).isSameAs(other);
    }

    @Test
    void refusesToStartWithoutADelegate() {
        MaskingEncoder encoder = encoder(null);

        encoder.start();

        assertThat(encoder.isStarted()).isFalse();
        assertThat(context.getStatusManager().getCopyOfStatusList())
                .anyMatch(s -> s.getLevel() == Status.ERROR && s.getMessage().contains("No delegate"));
    }

    @Test
    void passesHeaderAndFooterThroughAndMasksEvents() {
        MaskingEncoder encoder = encoder(new FixedEncoder());
        encoder.start();

        assertThat(new String(encoder.headerBytes(), UTF_8)).isEqualTo("HEADER");
        assertThat(new String(encoder.footerBytes(), UTF_8)).isEqualTo("FOOTER");
        assertThat(new String(encoder.encode(event("mail to olena@example.test")), UTF_8))
                .isEqualTo("mail to o***@example.test");
        assertThat(encoder.encode(event(""))).isEmpty();
    }

    private MaskingEncoder encoder(FixedEncoder delegate) {
        MaskingEncoder encoder = new MaskingEncoder();
        encoder.setContext(context);
        encoder.setCharset(UTF_8);
        encoder.setDelegate(delegate);
        return encoder;
    }

    private LoggingEvent event(String message) {
        LoggingEvent event = new LoggingEvent();
        event.setMessage(message);
        return event;
    }
}
