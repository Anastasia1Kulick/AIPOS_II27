<p align="center">Министерство образования Республики Беларусь</p>
<p align="center">Учреждение образования</p>
<p align="center">“Брестский Государственный технический университет”</p>
<p align="center">Кафедра ИИТ</p>
<br><br><br><br><br><br><br>
<p align="center">Лабораторная работа №2</p>
<p align="center">По дисциплине “Аппаратное и Программное обеспечение сетей”</p>
<p align="center">Тема: “Организация UDP – сервера/клиента”</p>
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

Реализовать на языке программирования высокого уровня Java клент-серверное взамодействие, организованной через UDP.

# Задание на выполнение (Сервер)

8.1 Изучить теоретический материал, функции и классы пакета `java.net` и листинг программы реализации UDP-сервера. Получить индивидуальное задание у преподавателя.

8.2 Разработать программу работы UDP-эхо-сервера, выполняющую функции согласно варианта задания (см. приложения). В качестве клиента использовать программу `telnet`. Выполнить проверку программы согласно методике, приведенной в разделе 7.1. Продемонстрировать работу системы преподавателю.

8.3 Представить отчет, содержащий титульный лист, листинг программы с подробными комментариями основных фрагментов программы.

8.4 Подготовиться к защите лабораторной работы по теоретическому материалу, функциям, собственным результатам, полученным в ходе выполнения лабораторной.

# Задание на выполнение (Клиент)

9.1 Изучить теоретический материал, функции и классы пакета `java.net` и листинг программ реализации UDP-клиента.

9.2 Разработать программу работы UDP-клиента (см. приложения). Выполнить проверку программы с использованием разработанного в предыдущей лабораторной работе UDP-сервера. Продемонстрировать работу системы преподавателю.

9.3 Представить отчет, содержащий титульный лист, листинг программы с подробными комментариями основных фрагментов программы.

9.4 Подготовиться к защите лабораторной работы по теоретическому материалу, функциям, собственным результатам, полученным в ходе выполнения лабораторной.

# Ход работы

# Структура проекта

```text
src/
├── interfaces/
│   └── ConnectionHandler.java
├── udp/
│   ├── UDPClient.java
│   ├── UDPServer.java
│   └── UDPClientSession.java
└── main/
    ├── UDPClientServer.java
    └── UDPServerMain.java
```

# Код программы 

ConnectionHandler.java
```java
package interfaces;

public interface ConnectionHandler {

    void onConnect(String clientAddress);
    void onDisconnect(String clientAddress);
    void onReceive(String clientAddress, String message);
    
}
```

UdpClientMain.java
```java
package main;

import udp.UdpClient;

public class UdpClientMain {
    public static void main(String[] args) {
        try {
            new UdpClient().start();
        } catch (RuntimeException e) {
            System.err.println("Критическая ошибка: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
}
```

UdpServerMain.java
```java
package main;

import interfaces.ConnectionHandler;
import udp.UdpServer;

public class UdpServerMain implements ConnectionHandler {

    public static void main(String[] args) {
        int port = 666;

        UdpServerMain app = new UdpServerMain();
        UdpServer server = new UdpServer(port, app);

        Thread serverThread = new Thread(server::start);
        serverThread.start();

        System.out.println("Для остановки сервера нажмите Ctrl+C");
    }

    @Override
    public void onConnect(String clientAddress) {
        System.out.println(">>> Сессия начата: " + clientAddress);
    }

    @Override
    public void onDisconnect(String clientAddress) {
        System.out.println(">>> Сессия завершена: " + clientAddress);
    }

    @Override
    public void onReceive(String clientAddress, String message) {
    
    }
    
}
```

UdpClient.java
```java
package udp;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Scanner;

public class UdpClient {
    private DatagramSocket socket;
    private InetAddress serverAddress;
    private int serverPort;

    private boolean connected = false;
    private static final int BUFFER_SIZE = 2048;

    public void start() {
        Scanner scanner = new Scanner(System.in);

        try {
            socket = new DatagramSocket();

            socket.setSoTimeout(3000);

            System.out.println("UDP Клиент запущен.");
            System.out.println("Команды:");
            System.out.println("  connect <адрес> <порт> — задать сервер");
            System.out.println("  disconnect             — завершить сессию");
            System.out.println("  exit                   — выход");
            System.out.println("Другие строки отправляются серверу.");

            while (true) {
                String input = scanner.nextLine();

                if (input.startsWith("connect")) {
                    String[] parts = input.split(" ");
                    if (parts.length == 3) {
                        try {
                            serverAddress = InetAddress.getByName(parts[1]);
                            serverPort = Integer.parseInt(parts[2]);
                            connected = true;
                            System.out.println("Сервер задан: " +
                                    parts[1] + ":" + serverPort);
                        } catch (Exception e) {
                            System.err.println("Ошибка: " + e.getMessage());
                        }
                    } else {
                        System.out.println("Использование: connect <адрес> <порт>");
                    }
                } else if (input.equalsIgnoreCase("disconnect")) {
                    if (connected) {
                        send("disconnect " +
                                serverAddress.getHostAddress() + " " +
                                serverPort);
                        receive();
                        connected = false;
                        System.out.println("Сессия завершена.");
                    } else {
                        System.out.println("Сначала выполните connect.");
                    }
                } else if (input.equalsIgnoreCase("exit")) {
                    break;
                } else if (connected) {
                    send(input);
                    receive();
                } else {
                    System.out.println("Сначала выполните connect <адрес> <порт>");
                }
            }
        } catch (IOException e) {
            System.err.println("Ошибка клиента: " + e.getMessage());
        } finally {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        }
    }

    private void send(String message) throws IOException {
        byte[] data = message.getBytes();
        DatagramPacket packet = new DatagramPacket(
                data, data.length, serverAddress, serverPort);
        socket.send(packet);
    }

    private void receive() {
        try {
            byte[] buf = new byte[BUFFER_SIZE];
            DatagramPacket packet = new DatagramPacket(buf, buf.length);
            socket.receive(packet);
            String response = new String(packet.getData(), 0, packet.getLength());
            System.out.println("Ответ сервера: " + response);
        } catch (java.net.SocketTimeoutException e) {
            System.out.println("(набрано <64 символов, ожидается продолжение)");
        } catch (IOException e) {
            System.err.println("Ошибка приёма: " + e.getMessage());
        }
    }
    
}
```

UdpClientSession.java
```java
package udp;

import java.net.InetAddress;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class UdpClientSession {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private final InetAddress address;
    private final int port;

    private final char[] buffer = new char[64];
    private int charsRead = 0;
    private final LocalDateTime sessionStart;

    public UdpClientSession(InetAddress address, int port) {
        this.address = address;
        this.port = port;
        this.sessionStart = LocalDateTime.now();
    }

    public InetAddress getAddress() { return address; }
    public int getPort() { return port; }

    public String getKey() {
        return address.getHostAddress() + ":" + port;
    }

    public LocalDateTime getSessionStart() { return sessionStart; }

    public String addChar(char ch) {
        buffer[charsRead++] = ch;
        if (charsRead == buffer.length) {
            String group = new String(buffer, 0, buffer.length);
            charsRead = 0;
            return group;
        }
        return null;
    }

    public String formatStart() {
        return sessionStart.format(FORMATTER);
    }

}
```

UdpServer.java
```java
package udp;

import interfaces.ConnectionHandler;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class UdpServer {
    private final int port;
    private final ConnectionHandler handler;
    private DatagramSocket socket;

    private final Map<String, UdpClientSession> clients = new ConcurrentHashMap<>();

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private static final int GROUP_SIZE = 64;
    private static final int MIN_UNIQUE_CHARS = 3;
    private static final int BUFFER_SIZE = 2048;

    public UdpServer(int port, ConnectionHandler handler) {
        this.port = port;
        this.handler = handler;
    }

    public void start() {
        try {
            socket = new DatagramSocket(port);
            System.out.println("UDP-сервер запущен на порту " + port);

            byte[] receiveBuf = new byte[BUFFER_SIZE];

            while (!socket.isClosed()) {
                DatagramPacket packet = new DatagramPacket(receiveBuf, receiveBuf.length);
                socket.receive(packet);

                InetAddress clientAddr = packet.getAddress();
                int clientPort = packet.getPort();
                String key = clientAddr.getHostAddress() + ":" + clientPort;

                String data = new String(packet.getData(), 0, packet.getLength());
                System.out.println("[" + LocalDateTime.now().format(FORMATTER) +
                        "] Пакет от " + key + ": " + data);

                if (data.startsWith("disconnect")) {
                    handleDisconnect(key, clientAddr, clientPort);
                    continue;
                }

                UdpClientSession session = clients.computeIfAbsent(key, k -> {
                    UdpClientSession s = new UdpClientSession(clientAddr, clientPort);
                    logEvent("CONNECT", key + " (начало " + s.formatStart() + ")");
                    handler.onConnect(key);
                    return s;
                });

                for (int i = 0; i < data.length(); i++) {
                    char ch = data.charAt(i);

                    if (ch == '\n' || ch == '\r') continue;

                    String group = session.addChar(ch);

                    if (group != null) {
                        processGroup(session, group);
                        if (!clients.containsKey(key)) break;
                    }
                }
            }

        } catch (IOException e) {
            System.err.println("Ошибка UDP-сервера: " + e.getMessage());
        }
    }

    private void processGroup(UdpClientSession session, String group) {
        String key = session.getKey();

        logEvent("RECEIVED_GROUP", key + " | Данные: " + group);
        handler.onReceive(key, group);

        Map<Character, Integer> freq = new HashMap<>();
        for (char c : group.toCharArray()) {
            freq.put(c, freq.getOrDefault(c, 0) + 1);
        }

        if (freq.size() < MIN_UNIQUE_CHARS) {
            String errorMsg = "Ошибка: В группе менее " + MIN_UNIQUE_CHARS +
                    " уникальных символов. Сессия завершается.";
            System.out.println("[" + LocalDateTime.now().format(FORMATTER) +
                    "] " + errorMsg);

            sendResponse(session, errorMsg);

            clients.remove(key);
            logEvent("DISCONNECT", key + " (по ошибке)");
            handler.onDisconnect(key);
            return;
        }

        StringBuilder response = new StringBuilder();
        for (Map.Entry<Character, Integer> e : freq.entrySet()) {
            response.append("<").append(e.getKey())
                    .append("-").append(e.getValue()).append("> ");
        }
        String responseStr = response.toString().trim();

        System.out.println("[" + LocalDateTime.now().format(FORMATTER) +
                "] Отправка статистики для " + key + ": " + responseStr);
        sendResponse(session, responseStr);
    }

    private void sendResponse(UdpClientSession session, String message) {
        try {
            byte[] data = message.getBytes();
            DatagramPacket packet = new DatagramPacket(
                    data, data.length,
                    session.getAddress(), session.getPort());
            socket.send(packet);
        } catch (IOException e) {
            System.err.println("Ошибка отправки: " + e.getMessage());
        }
    }

    private void handleDisconnect(String key, InetAddress addr, int port) {
        UdpClientSession removed = clients.remove(key);
        if (removed != null) {
            logEvent("DISCONNECT", key + " (по команде)");
            handler.onDisconnect(key);
            sendResponse(removed, "Сессия завершена по команде disconnect.");
        } else {
            try {
                byte[] data = "Сессия не найдена.".getBytes();
                socket.send(new DatagramPacket(data, data.length, addr, port));
            } catch (IOException ignored) {}
        }
    }

    private void logEvent(String event, String info) {
        System.out.println("[" + LocalDateTime.now().format(FORMATTER) +
                "] " + event + " для " + info);
    }

    public void stop() {
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }
    
}
```

# Результат программы

Server:
```
Для остановки сервера нажмите Ctrl+C
UDP-сервер запущен на порту 666
[2026-10-11 14:45:09.257] Пакет от 127.0.0.1:62999: aaabbbcccaaabbbcccaaabbbcccaaabbbcccaaabbbcccaaabbbcccaaabbbccca
[2026-10-11 14:45:09.261] CONNECT для 127.0.0.1:62999 (начало 2026-10-11 14:45:09.260)
>>> Сессия начата: 127.0.0.1:62999
[2026-10-11 14:45:09.262] RECEIVED_GROUP для 127.0.0.1:62999 | Данные: aaabbbcccaaabbbcccaaabbbcccaaabbbcccaaabbbcccaaabbbcccaaabbbccca
[2026-10-11 14:45:09.262] Отправка статистики для 127.0.0.1:62999: <a-22> <b-21> <c-21>
[2026-10-11 14:45:26.890] Пакет от 127.0.0.1:62999: abababababababababababababababababababababababababababababababab
[2026-10-11 14:45:26.890] RECEIVED_GROUP для 127.0.0.1:62999 | Данные: abababababababababababababababababababababababababababababababab
[2026-10-11 14:45:26.890] Ошибка: В группе менее 3 уникальных символов. Сессия завершается.
[2026-10-11 14:45:26.892] DISCONNECT для 127.0.0.1:62999 (по ошибке)
>>> Сессия завершена: 127.0.0.1:62999
[2026-10-11 14:45:37.930] Пакет от 127.0.0.1:62999: aaabbbcccaaabbbcccaaabbbcccaaabbbcccaaabbbcccaaabbbcccaaabbbccca
[2026-10-11 14:45:37.930] CONNECT для 127.0.0.1:62999 (начало 2026-10-11 14:45:37.930)
>>> Сессия начата: 127.0.0.1:62999
[2026-10-11 14:45:37.931] RECEIVED_GROUP для 127.0.0.1:62999 | Данные: aaabbbcccaaabbbcccaaabbbcccaaabbbcccaaabbbcccaaabbbcccaaabbbccca
[2026-10-11 14:45:37.931] Отправка статистики для 127.0.0.1:62999: <a-22> <b-21> <c-21>
```

Client:
```
UDP Клиент запущен.
Команды:
  connect <адрес> <порт> — задать сервер
  disconnect             — завершить сессию
  exit                   — выход
Другие строки отправляются серверу.
connect localhost 666
Сервер задан: localhost:666
aaabbbcccaaabbbcccaaabbbcccaaabbbcccaaabbbcccaaabbbcccaaabbbccca
Ответ сервера: <a-22> <b-21> <c-21>
abababababababababababababababababababababababababababababababab
Ответ сервера: Ошибка: В группе менее 3 уникальных символов. Сессия завершается.
aaabbbcccaaabbbcccaaabbbcccaaabbbcccaaabbbcccaaabbbcccaaabbbccca
Ответ сервера: <a-22> <b-21> <c-21>
```