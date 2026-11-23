import java.net.*;

public class UDP_Server {
    public static void main(String[] args) {
        int port = 5006; // Порт варианта 5

        try (DatagramSocket serverSocket = new DatagramSocket(port)) {
            System.out.println("UDP Server is listening on port " + port + "...");

            byte[] receiveBuffer = new byte[256];
            DatagramPacket receivePacket = new DatagramPacket(
                    receiveBuffer, receiveBuffer.length);

            // Ждём первую датаграмму (метод блокирующий)
            serverSocket.receive(receivePacket);

            String message = new String(receivePacket.getData(), 0, 
                    receivePacket.getLength());
            System.out.println("Received: " + message);

            // Формируем ответ
            String response = "UDP: Datagram received!";
            byte[] sendBuffer = response.getBytes();

            // Отправляем ответ обратно тому, кто прислал
            DatagramPacket sendPacket = new DatagramPacket(
                    sendBuffer, sendBuffer.length,
                    receivePacket.getAddress(), receivePacket.getPort());
            serverSocket.send(sendPacket);

        } catch (Exception e) {
            System.err.println("UDP Server error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}