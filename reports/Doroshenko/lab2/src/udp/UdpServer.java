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