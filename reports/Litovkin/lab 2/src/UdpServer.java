import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public class UdpServer {

    private static final String HOST = "127.0.0.1";
    private static final int PORT = 666;
    private static final int GROUP_SIZE = 64;

    public static void main(String[] args) {

        try (var socket = new DatagramSocket(PORT, InetAddress.getByName(HOST))) {

            System.out.println("UDP server started: " + HOST + ":" + PORT);

            while (true) {

                byte[] buffer = new byte[2048];
                var request = new DatagramPacket(buffer, buffer.length);

                socket.receive(request);

                String message = new String(
                        request.getData(),
                        0,
                        request.getLength(),
                        StandardCharsets.UTF_8
                );

                System.out.println("Client: " + message);

                if (message.equalsIgnoreCase("disconnect " + HOST + " " + PORT)) {
                    System.out.println("Client disconnected. Server stopped.");
                    break;
                }

                if (message.length() != GROUP_SIZE) {
                    send(socket, request,
                            "Ошибка: необходимо передать ровно 64 символа.");
                    continue;
                }

                Map<Character, Integer> frequencies = new LinkedHashMap<>();

                for (char symbol : message.toCharArray()) {
                    frequencies.put(
                            symbol,
                            frequencies.getOrDefault(symbol, 0) + 1
                    );
                }

                if (frequencies.size() < 3) {
                    send(socket, request,
                            "Ошибка: различных символов меньше 3. Сервер завершает работу.");
                    break;
                }

                StringBuilder result = new StringBuilder("<");

                for (var entry : frequencies.entrySet()) {
                    result.append("«")
                            .append(entry.getKey())
                            .append("-")
                            .append(entry.getValue())
                            .append("», ");
                }

                result.setLength(result.length() - 2);
                result.append(">");

                send(socket, request, result.toString());
            }

        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
        }
    }

    private static void send(
            DatagramSocket socket,
            DatagramPacket request,
            String message
    ) throws IOException {

        byte[] data = message.getBytes(StandardCharsets.UTF_8);

        socket.send(new DatagramPacket(
                data,
                data.length,
                request.getAddress(),
                request.getPort()
        ));
    }
}