import java.io.*;
import java.net.*;

public class TCP_Server {
    public static void main(String[] args) {
        int port = 5005; // Порт варианта 5

        // try-with-resources автоматически закроет серверный сокет
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("TCP Server is listening on port " + port + "...");

            // accept() блокирует выполнение, пока не подключится клиент
            try (Socket clientSocket = serverSocket.accept()) {
                System.out.println("Client connected! IP: " 
                        + clientSocket.getInetAddress());

                // Получаем потоки для чтения и записи
                BufferedReader in = new BufferedReader(
                        new InputStreamReader(clientSocket.getInputStream()));
                PrintWriter out = new PrintWriter(
                        clientSocket.getOutputStream(), true);

                // Читаем строку от клиента
                String message = in.readLine();
                System.out.println("Received from client: " + message);

                // Отправляем ответ
                out.println("TCP: Data received successfully!");
            }
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}