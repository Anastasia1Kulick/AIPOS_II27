import java.io.*;
import java.net.*;

public class TCP_Client {
    public static void main(String[] args) {
        String host = "127.0.0.1"; // IP сервера (для локального теста)
        int port = 5005;           // Порт варианта 5

        // try-with-resources автоматически закроет сокет
        try (Socket socket = new Socket(host, port)) {
            System.out.println("Connected to server " + host + ":" + port);

            PrintWriter out = new PrintWriter(
                    socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream()));

            // Отправляем сообщение
            String message = "Hello from TCP Client (Variant 5)";
            out.println(message);

            // Читаем ответ сервера
            String response = in.readLine();
            System.out.println("Server response: " + response);

        } catch (IOException e) {
            System.err.println("Client error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}