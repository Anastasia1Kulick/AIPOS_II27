<p align="center">Министерство образования Республики Беларусь</p>
<p align="center">Учреждение образования</p>
<p align="center">“Брестский Государственный технический университет”</p>
<p align="center">Кафедра ИИТ</p>
<br><br><br><br><br><br><br>
<p align="center">Лабораторная работа №1</p>
<p align="center">По дисциплине “Аппаратное и Программное обеспечение сетей”</p>
<p align="center">Тема: “Организация TCP – сервера/клиента”</p>
<p align="center">Вариант: 4</p>
<br><br><br><br><br>
<p align="right">Выполнил:</p>
<p align="right">Студент 3 курса</p>
<p align="right">Группы ИИ-27</p>
<p align="right">Дорошенко М.Д.</p>
<p align="right">Проверил:</p>
<p align="right">Кулик А.Д.</p>
<br><br><br><br><br>
<p align="center">Брест 2026</p>

# Цель

Реализовать на языке программирования высокого уровня Java клент-серверное взамодействие, организованной через TCP.

# Задание на выполнение (Сервер)

8.1 Изучить теоретический материал, функции и классы пакета `java.net` и листинг программы реализации TCP-сервера. Получить индивидуальное задание у преподавателя.

8.2 Разработать программу работы TCP-эхо-сервера, выполняющую функции согласно варианта задания (см. приложения). В качестве клиента использовать программу `telnet`. Выполнить проверку программы согласно методике, приведенной в разделе 7.1. Продемонстрировать работу системы преподавателю.

8.3 Представить отчет, содержащий титульный лист, листинг программы с подробными комментариями основных фрагментов программы.

8.4 Подготовиться к защите лабораторной работы по теоретическому материалу, функциям, собственным результатам, полученным в ходе выполнения лабораторной.

# Задание на выполнение (Клиент)

9.1 Изучить теоретический материал, функции и классы пакета `java.net` и листинг программ реализации TCP-клиента.

9.2 Разработать программу работы TCP-клиента (см. приложения). Выполнить проверку программы с использованием разработанного в предыдущей лабораторной работе TCP-сервера. Продемонстрировать работу системы преподавателю.

9.3 Представить отчет, содержащий титульный лист, листинг программы с подробными комментариями основных фрагментов программы.

9.4 Подготовиться к защите лабораторной работы по теоретическому материалу, функциям, собственным результатам, полученным в ходе выполнения лабораторной.

# Ход работы

Возникает закономерный вопрос: Почему не C++? Для этого я выделю конкретные причины:

1) Безопасность: не надо будет захламлять нашу голову управлением памятью: освобождении буферов, работе с указателями, утечках и другими 'интересными вещами'.
2) 'Легкость': На C++ составит чуть более 100 строк кода, в то время как на Java всего лишь 25-40 строк.
3) В Java работа с сетью построена на объектах с говорящими именами: `ServerSocket`, `Socket`, `InputStream`, `OutputStream`, `BufferedReader`. В C/C++ аналогичный функционал реализуется через низкоуровневые функции вроде `socket()`, `bind()`, `listen()`, `accept()`, `recv()`, `send()`, `WSAStartup()`/`WSACleanup()`, что делает код менее наглядным и более платформозависимым.

После небольшого объяснения, перейдем к лабе.

# Структура проекта

```text
src/
├── interfaces/
│   └── ConnectionHandler.java
├── server/
│   ├── TcpServer.java
│   └── ClientSession.java
├── client/
|   ├── EventLogger.java
│   └── TcpClient.java
└── main/
    ├── ClientServer.java
    └── ServerMain.java
```

# Код программы : Клиент [ src/client ]

EventLogger.java:
```java
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
```

TcpClient.java:
```java
package client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.file.Path;
import java.util.Scanner;

public class TcpClient {

    private static final String CONNECT_COMMAND = "connect";
    private static final String DISCONNECT_COMMAND = "disconnect";
    private static final String EXIT_COMMAND = "exit";
    private static final String LOG_FILE = "client_protocol.log";

    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private boolean connected = false;

    private EventLogger logger;

    public void start() {
        try {
            logger = new EventLogger(Path.of(LOG_FILE));
        } catch (IOException e) {
            System.err.println("Не удалось открыть файл протокола: " + e.getMessage());
            return;
        }

        printHelp();

        @SuppressWarnings("resource") // System.in закрывать не нужно
        Scanner scanner = new Scanner(System.in);

        try {
            while (true) {
                if (!scanner.hasNextLine()) {
                    break;
                }
                String input = scanner.nextLine();
                if (!handleInput(input)) {
                    break;
                }
            }
        } finally {
            disconnect();
            logger.close();
        }
    }

    private void printHelp() {
        System.out.println("TCP Клиент (вариант 5, отправка по Enter).");
        System.out.println("Команды:");
        System.out.println("  connect <адрес> <порт>  — подключиться к серверу");
        System.out.println("  disconnect              — разорвать соединение");
        System.out.println("  exit                    — выйти из клиента");
        System.out.println("Отправка строки — клавиша Enter.");
    }

    /** Возвращает false, если нужно выйти из клиента. */
    private boolean handleInput(String input) {
        if (input.startsWith(CONNECT_COMMAND)) {
            handleConnectCommand(input);
        } else if (input.startsWith(DISCONNECT_COMMAND)) {
            disconnect();
        } else if (input.equalsIgnoreCase(EXIT_COMMAND)) {
            return false;
        } else {
            sendData(input);
        }
        return true;
    }

    private void handleConnectCommand(String input) {
        String[] parts = input.split(" ");
        if (parts.length != 3) {
            System.out.println("Использование: connect <адрес> <порт>");
            return;
        }
        try {
            int port = Integer.parseInt(parts[2]);
            connect(parts[1], port);
        } catch (NumberFormatException e) {
            System.out.println("Порт должен быть числом.");
        }
    }

    private void connect(String address, int port) {
        if (connected) {
            System.out.println("Уже подключено.");
            return;
        }
        try {
            socket = new Socket(address, port);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            connected = true;

            // 1) Логируем начало соединения
            logger.logConnectionStart(address, port);
            System.out.println("Подключено к " + address + ":" + port);
        } catch (IOException e) {
            System.err.println("Не удалось подключиться: " + e.getMessage());
        }
    }

    private void sendData(String line) {
        if (!connected || out == null) {
            System.out.println("Нет подключения. Используйте 'connect <адрес> <порт>'");
            return;
        }
        out.println(line);

        try {
            String response;
            while ((response = in.readLine()) != null) {
                // 3) Логируем принимаемую строку
                logger.logIncoming(response);
                if (response.startsWith("Ошибка") || response.contains("-")) {
                    break;
                }
            }
        } catch (IOException e) {
            System.err.println("Ошибка чтения: " + e.getMessage());
            disconnect();
        }
    }

    private void disconnect() {
        if (!connected) {
            return;
        }
        try {
            if (out != null) {
                String addr = socket.getInetAddress().getHostAddress();
                int port = socket.getPort();
                out.println(DISCONNECT_COMMAND + " " + addr + " " + port);

                // 1) Логируем окончание соединения
                logger.logConnectionEnd(addr, port);
            }
            socket.close();
            connected = false;
            System.out.println("Соединение разорвано.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        new TcpClient().start();
    }
}
```

# Код программы : Интерфейсы [ src/interfaces.java ]

ConnectionHandler.java:
```java
package interfaces;

/**
 * Интерфейс для обработки событий жизненного цикла TCP-соединения.
 * Позволяет отделить логику сервера от логики обработки уведомлений
 * (логирование, статистика, UI и т.д.).
 */
public interface ConnectionHandler {

    /**
     * Вызывается при подключении нового клиента.
     * @param clientAddress строковое представление адреса клиента (IP:порт)
     */
    void onConnect(String clientAddress);

    /**
     * Вызывается при отключении клиента (штатном или аварийном).
     * @param clientAddress строковое представление адреса клиента
     */
    void onDisconnect(String clientAddress);

    /**
     * Вызывается при получении сервером очередной порции данных.
     * @param clientAddress адрес клиента
     * @param message       полученные данные (в нашем случае — группа из 64 символов)
     */
    void onReceive(String clientAddress, String message);
}
```

# Код программы : Сервер [ src/client.java ]

ClientSession.java:
```java
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
```

TcpServer.java:
```java
package server;

import interfaces.ConnectionHandler;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Основной класс TCP-сервера.
 * Задача: слушать входящие подключения и для каждого клиента
 * запускать отдельный поток обработки (ClientSession).
 */
public class TcpServer {

    /** Порт, на котором сервер принимает подключения. */
    private final int port;

    /** Обработчик событий (реализация интерфейса ConnectionHandler). */
    private final ConnectionHandler handler;

    /** Сокет сервера, принимающий входящие соединения. */
    private ServerSocket serverSocket;

    /**
     * Пул потоков для параллельной обработки клиентов.
     * newCachedThreadPool() создаёт новый поток при необходимости
     * и переиспользует уже существующие — это оптимально для сервера,
     * обслуживающего произвольное число клиентов.
     */
    private final ExecutorService threadPool = Executors.newCachedThreadPool();

    /** Флаг работы сервера. volatile — для корректной видимости между потоками. */
    private volatile boolean running = false;

    public TcpServer(int port, ConnectionHandler handler) {
        this.port = port;
        this.handler = handler;
    }

    /**
     * Запускает сервер: открывает ServerSocket и в бесконечном цикле
     * принимает подключения, передавая каждое в отдельный поток.
     */
    public void start() {
        try {
            // Открытие слушающего сокета. Если порт занят — будет исключение.
            serverSocket = new ServerSocket(port);
            running = true;
            System.out.println("Сервер запущен на порту " + port);

            // Основной цикл приёма подключений.
            while (running) {
                // accept() блокируется, пока не придёт клиент.
                Socket clientSocket = serverSocket.accept();

                // Каждое соединение обрабатывается в отдельном потоке пула.
                threadPool.execute(new ClientSession(clientSocket, handler));
            }
        } catch (IOException e) {
            // Если исключение возникло не из-за остановки сервера — логируем.
            if (running) {
                System.err.println("Ошибка сервера: " + e.getMessage());
            }
        }
    }

    /**
     * Корректно останавливает сервер: закрывает ServerSocket
     * и завершает пул потоков.
     */
    public void stop() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            threadPool.shutdown();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
```

# Код программы : Приложение [ src/main.java ]

ClientMain.java
```java
package main;

import client.TcpClient;

/**
 * Точка входа клиентского приложения.
 * Отдельный файл Main для клиента согласно требованиям лабораторной работы.
 */
public class ClientMain {
    public static void main(String[] args) {
        try {
            new TcpClient().start();
        } catch (RuntimeException e) {
            System.err.println("Критическая ошибка клиента: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
```

ServerMain.java:
```java
package main;

import interfaces.ConnectionHandler;
import server.TcpServer;

/**
 * Точка входа приложения.
 * Реализует интерфейс ConnectionHandler, чтобы получать события
 * от сервера и выводить их в консоль.
 */
public class ServerMain implements ConnectionHandler {

    public static void main(String[] args) {
        // Порт по умолчанию.
        int port = 8080;

        // Если передан аргумент — используем его как порт.
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Неверный формат порта. Используется 8080.");
            }
        }

        // Создаём обработчик событий и сервер.
        ServerMain app = new ServerMain();
        TcpServer server = new TcpServer(port, app);

        // Запускаем сервер в отдельном потоке, чтобы main не блокировался.
        Thread serverThread = new Thread(server::start);
        serverThread.start();

        System.out.println("Для остановки сервера нажмите Ctrl+C");
    }

    @Override
    public void onConnect(String clientAddress) {
        System.out.println(">>> Клиент подключился: " + clientAddress);
    }

    @Override
    public void onDisconnect(String clientAddress) {
        System.out.println(">>> Клиент отключился: " + clientAddress);
    }

    @Override
    public void onReceive(String clientAddress, String message) {
        // Дополнительная обработка входящих сообщений.
    }
}
```