package Kozel.lab1.src;
import java.net.ServerSocket;
import java.net.Socket;
import java.io.*;
import java.nio.charset.StandardCharsets;

public class FileServer { 
    public static void main(String[] args) {
        int port = 8080;
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Server started on port " + port);
            
            while (true) {
                System.out.println("Waiting for client...");
                Socket clientSocket = serverSocket.accept();
                System.out.println("Client connected: " + clientSocket.getInetAddress());
                handleClient(clientSocket);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void handleClient(Socket clientSocket) {
        try (
            BufferedReader in = new BufferedReader(
                new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8)
            );
            PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)
        ) {
            String command = in.readLine();
            if (command == null) return;
            
            System.out.println("Received command: " + command);
            
            String[] parts = command.split(" ", 2);
            
            if (parts.length < 2 || !parts[0].equals("load")) {
                out.println("Error: Invalid command. Use 'load <filename>'");
                clientSocket.close(); 
                return;
            }
            
            String fileName = parts[1].trim();
            File file = new File(fileName);
            
            // Проверка наличия файла 
            if (!file.exists() || !file.isFile()) {
                out.println("Error: File not found - " + fileName);
                System.out.println("File not found, closing connection.");
                clientSocket.close(); 
                return;
            }
            
            // Читаем и отправляем файл
            try (BufferedReader fileReader = new BufferedReader(
                    new FileReader(file, StandardCharsets.UTF_8))) {
                String line;
                while ((line = fileReader.readLine()) != null) {
                    out.println(line);
                }
                System.out.println("File sent successfully.");
            }
            
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}