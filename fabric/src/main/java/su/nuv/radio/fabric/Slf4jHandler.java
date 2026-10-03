package su.nuv.radio.fabric;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

/** Sends the core's java.util.logging records to the server log, as Paper does for plugins. */
final class Slf4jHandler extends Handler {

    private final Logger logger = LoggerFactory.getLogger("JukeboxRadio");

    static java.util.logging.Logger install(java.util.logging.Logger logger) {
        logger.setUseParentHandlers(false);
        for (Handler handler : logger.getHandlers()) {
            logger.removeHandler(handler);
        }
        logger.addHandler(new Slf4jHandler());
        return logger;
    }

    @Override
    public void publish(LogRecord record) {
        final String message = record.getMessage();
        final Throwable error = record.getThrown();
        final int level = record.getLevel().intValue();
        if (level >= Level.SEVERE.intValue()) {
            this.logger.error(message, error);
        } else if (level >= Level.WARNING.intValue()) {
            this.logger.warn(message, error);
        } else if (level >= Level.INFO.intValue()) {
            this.logger.info(message, error);
        } else {
            this.logger.debug(message, error);
        }
    }

    @Override
    public void flush() {
    }

    @Override
    public void close() {
    }
}
