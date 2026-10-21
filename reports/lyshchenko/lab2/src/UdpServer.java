import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class UdpServer {
    public static final int PORT = 8080;
    private static final int GROUP_SIZE = 48;
    private static final String TERMINATION = "~#~";
    private static final int PACKET_SIZE = 1024;

    private static final Map<String, ClientSession> sessions = new HashMap<>();

    static class ClientSession {
        StringBuilder buffer = new StringBuilder();
        int totalSessionCount = 0;
    }

    public static void main(String[] args) throws IOException {
        DatagramSocket socket = new DatagramSocket(PORT);
        System.out.println("UDP сервер запущен на порту " + PORT);

        byte[] receiveData = new byte[PACKET_SIZE];

        while (true) {
            DatagramPacket request = new DatagramPacket(receiveData, receiveData.length);
            socket.receive(request);

            String clientKey = request.getAddress().getHostAddress() + ":" + request.getPort();
            
            ClientSession session = sessions.computeIfAbsent(clientKey, k -> new ClientSession());

            String received = new String(request.getData(), 0, request.getLength(), StandardCharsets.UTF_8);

            for (int i = 0; i < received.length(); i++) {
                char c = received.charAt(i);

                if (c == '\r') {
                    continue;
                }

                if (c == '\n') {
                    if (session.buffer.toString().contains(TERMINATION)) {
                        sendResponse(socket, request, "Сеанс завершён");
                        System.out.println("[" + clientKey + "] Получен сигнал завершения ~#~");
                        sessions.remove(clientKey);
                        break;
                    }

                    while (session.buffer.length() >= GROUP_SIZE) {
                        String group = session.buffer.substring(0, GROUP_SIZE);
                        session.buffer.delete(0, GROUP_SIZE);

                        int checksum = 0;
                        for (int j = 0; j < group.length(); j++) {
                            checksum += (int) group.charAt(j);
                        }

                        String response = "Группа принята. Контрольная сумма: " + checksum 
                                        + ". Всего принято от начала сеанса: " + session.totalSessionCount + ".";
                        sendResponse(socket, request, response);
                        System.out.println("[" + clientKey + "] Отправлено: " + response);
                    }
                    continue;
                }

                session.buffer.append(c);
                session.totalSessionCount++;

                if (session.buffer.toString().contains(TERMINATION)) {
                    sendResponse(socket, request, "Сеанс завершён");
                    System.out.println("[" + clientKey + "] Получен сигнал завершения ~#~");
                    sessions.remove(clientKey);
                    break;
                }
            }
        }
    }

    private static void sendResponse(DatagramSocket socket, DatagramPacket request, String message) throws IOException {
        byte[] responseData = message.getBytes(StandardCharsets.UTF_8);
        DatagramPacket response = new DatagramPacket(responseData, responseData.length, 
                                                     request.getAddress(), request.getPort());
        socket.send(response);
    }
}