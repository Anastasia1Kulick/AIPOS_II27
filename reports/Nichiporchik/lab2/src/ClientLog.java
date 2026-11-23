import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

public class ClientLog {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private final Logger logger;
    private final FileHandler handler;

    public ClientLog(String fileName) {
        try {
            logger = Logger.getLogger("client-log-" + fileName);
            logger.setUseParentHandlers(false);
            logger.setLevel(Level.INFO);
            handler = new FileHandler(fileName, true);
            handler.setEncoding("UTF-8");
            handler.setFormatter(new Formatter() {
                public String format(LogRecord record) {
                    LocalDateTime t = LocalDateTime.ofInstant(Instant.ofEpochMilli(record.getMillis()), ZoneId.systemDefault());
                    return t.format(TIME) + " | " + record.getMessage() + System.lineSeparator();
                }
            });
            logger.addHandler(handler);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void info(String message) {
        logger.info(message);
    }

    public void close() {
        handler.close();
    }
}
