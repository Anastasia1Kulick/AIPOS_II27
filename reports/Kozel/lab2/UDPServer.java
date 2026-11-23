import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class UDPServer {
    public static final int PORT = 666;
    // Storage of buffers for each client (key: "IP:Port")
    private static Map<String, StringBuilder> clientBuffers = new HashMap<>();

    public static void main(String[] args) {
        System.out.println("UDP Server started on port " + PORT + "...");

        try (DatagramSocket socket = new DatagramSocket(PORT)) {
            byte[] buffer = new byte[1024];

            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                String receivedText = new String(packet.getData(), 0, packet.getLength());
                InetAddress clientAddress = packet.getAddress();
                int clientPort = packet.getPort();
                String clientKey = clientAddress.getHostAddress() + ":" + clientPort;

                // Get or create buffer for this client
                StringBuilder clientBuffer = clientBuffers.computeIfAbsent(clientKey, k -> new StringBuilder());
                clientBuffer.append(receivedText);

                // Process data in chunks of 64 symbols
                while (clientBuffer.length() >= 64) {
                    String chunk = clientBuffer.substring(0, 64);
                    clientBuffer.delete(0, 64); // Remove processed 64 symbols

                    String response = processChunk(chunk);

                    // If unique symbols < 3, "disconnect" client (remove from list)
                    if (response.startsWith("Error")) {
                        clientBuffers.remove(clientKey);
                    }

                    // Send response to client
                    byte[] responseData = response.getBytes();
                    DatagramPacket responsePacket = new DatagramPacket(
                            responseData, responseData.length, clientAddress, clientPort);
                    socket.send(responsePacket);

                    System.out.println("Sent to " + clientKey + ": " + response);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Process a group of 64 symbols
    private static String processChunk(String chunk) {
        Map<Character, Integer> charCounts = new LinkedHashMap<>();

        // Count each symbol
        for (char c : chunk.toCharArray()) {
            charCounts.put(c, charCounts.getOrDefault(c, 0) + 1);
        }

        // Check condition: number of different symbols < 3
        if (charCounts.size() < 3) {
            return "Error: number of different symbols is less than 3. Session terminated.";
        }

        // Build response string: < "symbol1-count1", "symbol2-count2", etc. >
        StringBuilder result = new StringBuilder("< ");
        for (Map.Entry<Character, Integer> entry : charCounts.entrySet()) {
            result.append("\"").append(entry.getKey()).append("-").append(entry.getValue()).append("\", ");
        }

        // Remove last comma and space, close bracket
        result.setLength(result.length() - 2);
        result.append(" >");

        return result.toString();
    }
}