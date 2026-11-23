import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class UDPServer {
    public static final int PORT = 666;

    private static class Session {
        StringBuilder group10 = new StringBuilder();
        StringBuilder group48 = new StringBuilder();
        StringBuilder tail = new StringBuilder();
        int totalChars = 0;
        boolean finished = false;
    }

    private static final Map<String, Session> sessions = new HashMap<>();

    public static void main(String[] args) throws IOException {
        try (DatagramSocket socket = new DatagramSocket(PORT)) {
            System.out.println("Server started on port " + PORT);
            byte[] buf = new byte[2048];

            while (true) {
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                socket.receive(packet);

                String clientKey = packet.getAddress().getHostAddress() + ":" + packet.getPort();
                String received = new String(packet.getData(), packet.getOffset(), packet.getLength(), StandardCharsets.UTF_8);
                System.out.println("From " + clientKey + " -> " + received);

                Session session = sessions.computeIfAbsent(clientKey, k -> new Session());

                if (session.finished) {
                    send(socket, packet, "Сеанс завершён\n");
                    continue;
                }

                StringBuilder response = new StringBuilder();
                boolean sessionEnded = false;

                for (int i = 0; i < received.length(); i++) {
                    char c = received.charAt(i);
                    session.totalChars++;

                    session.tail.append(c);
                    if (session.tail.length() > 3) {
                        session.tail.deleteCharAt(0);
                    }
                    if (session.tail.length() == 3 && session.tail.toString().equals("~#~")) {
                        response.append("Конец сеанса\n");
                        sessionEnded = true;
                        break;
                    }

                    session.group10.append(c);
                    if (session.group10.length() == 10) {
                        int checksum10 = 0;
                        for (int j = 0; j < session.group10.length(); j++) {
                            checksum10 += (int) session.group10.charAt(j);
                        }
                        response.append("10: '")
                                .append(session.group10)
                                .append("' -> ")
                                .append(checksum10)
                                .append("\n");
                        System.out.println("10-symbol checksum=" + checksum10);
                        session.group10.setLength(0);
                    }

                    session.group48.append(c);
                    if (session.group48.length() == 48) {
                        int checksum48 = 0;
                        for (int j = 0; j < session.group48.length(); j++) {
                            checksum48 += (int) session.group48.charAt(j);
                        }
                        response.append("48: checksum=")
                                .append(checksum48)
                                .append(", total=")
                                .append(session.totalChars)
                                .append("\n");
                        System.out.println("48-symbol checksum=" + checksum48 + ", total=" + session.totalChars);
                        session.group48.setLength(0);
                    }
                }

                if (response.length() > 0) {
                    send(socket, packet, response.toString());
                }

                if (sessionEnded) {
                    session.finished = true;
                    System.out.println("Session " + clientKey + " finished (~#~)");
                }
            }
        }
    }

    private static void send(DatagramSocket socket, DatagramPacket clientPacket, String message) throws IOException {
        byte[] data = message.getBytes(StandardCharsets.UTF_8);
        DatagramPacket response = new DatagramPacket(data, data.length, clientPacket.getAddress(), clientPacket.getPort());
        socket.send(response);
    }
}