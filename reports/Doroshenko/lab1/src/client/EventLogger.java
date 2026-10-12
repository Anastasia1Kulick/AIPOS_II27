package client;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Ведение файла протокола событий.
 * Для варианта 5 логируем:
 *   1) время начала и окончания соединения;
 *   3) принимаемую от сервера строку и время приёма.
 * Передаваемая серверу строка (2) в протокол не пишется.
 */
public class EventLogger implements AutoCloseable {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final BufferedWriter writer;

    public EventLogger(Path file) throws IOException {
        this.writer = Files.newBufferedWriter(
                file,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
        );
    }

    /** 1) Начало соединения. */
    public void logConnectionStart(String address, int port) {
        write("СОЕДИНЕНИЕ НАЧАЛО: " + address + ":" + port);
    }

    /** 1) Окончание соединения. */
    public void logConnectionEnd(String address, int port) {
        write("СОЕДИНЕНИЕ КОНЕЦ: " + address + ":" + port);
    }

    /** 3) Приём строки от сервера. */
    public void logIncoming(String line) {
        write("ПРИНЯТО: " + line);
    }

    private void write(String message) {
        String entry = "[" + LocalDateTime.now().format(FORMATTER) + "] " + message;
        try {
            writer.write(entry);
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            System.err.println("Ошибка записи в протокол: " + e.getMessage());
        }
        // Дублируем в консоль для наглядности
        System.out.println(entry);
    }

    @Override
    public void close() {
        try {
            writer.close();
        } catch (IOException ignored) {
        }
    }
}