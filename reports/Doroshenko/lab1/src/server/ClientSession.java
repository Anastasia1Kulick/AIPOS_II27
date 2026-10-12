package server;

import interfaces.ConnectionHandler;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Класс обработки одного клиентского соединения.
 * Реализует логику варианта 4:
 *  - накапливает входные символы в буфер размером 64;
 *  - при заполнении буфера считает частоту символов;
 *  - если уникальных символов < 3 — отправляет ошибку и рвёт соединение;
 *  - иначе — отправляет статистику вида <символ-количество>.
 *  Также поддерживает команду disconnect <адрес> <порт>.
 */
public class ClientSession implements Runnable {

    /** Сокет конкретного клиента. */
    private final Socket socket;

    /** Обработчик событий сервера (для логирования и уведомлений). */
    private final ConnectionHandler handler;

    /** Формат вывода даты/времени в логе. */
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** Размер группы символов, после которой выполняется анализ. */
    private static final int GROUP_SIZE = 64;

    /** Минимальное допустимое число уникальных символов в группе. */
    private static final int MIN_UNIQUE_CHARS = 3;

    public ClientSession(Socket socket, ConnectionHandler handler) {
        this.socket = socket;
        this.handler = handler;
    }

    /**
     * Основной метод обработки соединения.
     */
    @Override
    public void run() {
        // Формируем строковое представление адреса клиента для логов.
        String clientAddress = socket.getInetAddress() + ":" + socket.getPort();

        // Логируем факт подключения (пункт 1 протокола событий варианта 5).
        logEvent("CONNECT", clientAddress);
        handler.onConnect(clientAddress);

        // try-with-resources автоматически закроет потоки при выходе.
        try (InputStream in = socket.getInputStream();
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

            // Буфер накапливаемых символов (ровно GROUP_SIZE).
            char[] buffer = new char[GROUP_SIZE];

            // Текущее количество накопленных символов в буфере.
            int charsRead = 0;

            // Буфер для анализа строки команды disconnect.
            StringBuilder commandBuffer = new StringBuilder();

            int data;
            // Читаем по одному байту (символу). read() возвращает -1 при EOF.
            while ((data = in.read()) != -1) {
                char ch = (char) data;

                // Символы переноса строки используем как разделитель команд.
                if (ch == '\n' || ch == '\r') {
                    String line = commandBuffer.toString().trim();

                    // Проверяем команду disconnect <адрес> <порт>.
                    // Это реализует столбец "Возможность разрыва соединения".
                    if (line.startsWith("disconnect")) {
                        String[] parts = line.split(" ");
                        if (parts.length == 3) {
                            System.out.println("[" +
                                    LocalDateTime.now().format(FORMATTER) +
                                    "] Получена команда disconnect. Разрыв соединения.");
                            break; // выходим из цикла — finally закроет сокет
                        }
                    }

                    // Очищаем буфер команды и продолжаем — символы \n \r
                    // в группу из 64 символов не попадают.
                    commandBuffer.setLength(0);
                    continue;
                }

                // Накапливаем символы для потенциальной команды.
                commandBuffer.append(ch);

                // Кладём символ в буфер группы.
                buffer[charsRead++] = ch;

                // Как только набрали ровно 64 символа — анализируем группу.
                if (charsRead == GROUP_SIZE) {
                    String group = new String(buffer, 0, GROUP_SIZE);

                    // Лог полученной группы (пункт 2 протокола событий).
                    logEvent("RECEIVED_GROUP",
                            clientAddress + " | Данные: " + group);
                    handler.onReceive(clientAddress, group);

                    // Подсчёт частоты каждого символа через HashMap.
                    Map<Character, Integer> freq = new HashMap<>();
                    for (char c : buffer) {
                        freq.put(c, freq.getOrDefault(c, 0) + 1);
                    }

                    // Проверка условия: менее MIN_UNIQUE_CHARS уникальных символов.
                    if (freq.size() < MIN_UNIQUE_CHARS) {
                        String errorMsg = "Ошибка: В группе менее " +
                                MIN_UNIQUE_CHARS +
                                " уникальных символов. Соединение разрывается.";
                        System.out.println("[" +
                                LocalDateTime.now().format(FORMATTER) +
                                "] " + errorMsg);
                        out.println(errorMsg);
                        break; // разрываем соединение
                    }

                    // Формируем ответ вида <символ-количество> <символ-количество> ...
                    StringBuilder response = new StringBuilder();
                    for (Map.Entry<Character, Integer> e : freq.entrySet()) {
                        response.append("<").append(e.getKey())
                                .append("-").append(e.getValue()).append("> ");
                    }
                    String responseStr = response.toString().trim();

                    // Лог отправляемой статистики (пункт 3 протокола событий).
                    System.out.println("[" +
                            LocalDateTime.now().format(FORMATTER) +
                            "] Отправка статистики: " + responseStr);
                    out.println(responseStr);

                    // Сбрасываем счётчик — начинаем копить следующую группу.
                    charsRead = 0;
                }
            }

        } catch (IOException e) {
            // Аварийное завершение соединения.
            System.err.println("Ошибка сессии " + clientAddress + ": " + e.getMessage());
        } finally {
            // Блок finally выполняется всегда — логируем отключение
            // и закрываем сокет, освобождая ресурсы.
            logEvent("DISCONNECT", clientAddress);
            handler.onDisconnect(clientAddress);
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Вспомогательный метод для логирования событий с отметкой времени.
     */
    private void logEvent(String event, String address) {
        String time = LocalDateTime.now().format(FORMATTER);
        System.out.println("[" + time + "] " + event + " для " + address);
    }
}