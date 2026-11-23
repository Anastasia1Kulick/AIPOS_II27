import java.net.*;

public class UDP_Client {
    public static void main(String[] args) {
        String host = "127.0.0.1";
        int port = 5006; // Порт варианта 5

        try (DatagramSocket clientSocket = new DatagramSocket()) {
            // Формируем сообщение
            String message = "Hello from UDP Client (Variant 5)";
            byte[] sendBuffer = message.getBytes();

            InetAddress address = InetAddress.getByName(host);
            DatagramPacket sendPacket = new DatagramPacket(
                    sendBuffer, sendBuffer.length, address, port);

            // Отправляем датаграмму
            clientSocket.send(sendPacket);
            System.out.println("Datagram sent.");

            // Ждём ответ
            byte[] receiveBuffer = new byte[256];
            DatagramPacket receivePacket = new DatagramPacket(
                    receiveBuffer, receiveBuffer.length);
            clientSocket.receive(receivePacket);

            String response = new String(receivePacket.getData(), 0, 
                    receivePacket.getLength());
            System.out.println("Server response: " + response);

        } catch (Exception e) {
            System.err.println("UDP Client error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}