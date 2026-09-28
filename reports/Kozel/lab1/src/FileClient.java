package Kozel.lab1.src;
import java.net.Socket;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class FileClient {
    public static void main(String[] args) {
        String serverAddress = "localhost";
        int port = 8080;
        String logFileName = "client_log.txt";
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        try (
            Socket socket = new Socket(serverAddress, port);
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)
            );
            // Открываем файл для логирования
            PrintWriter logWriter = new PrintWriter(new FileWriter(logFileName, true), true)
        ) {
            String startTime = LocalDateTime.now().format(formatter);
            log("Connecting starts at " + startTime + " ", logWriter);
            System.out.println("Connected to server. Logging to " + logFileName);
            System.out.println("Enter the command (example 'load test.txt') and press Enter.");

            BufferedReader consoleIn = new BufferedReader(new InputStreamReader(System.in));
            String userInput;

            // Читаем ввод пользователя
            while ((userInput = consoleIn.readLine()) != null) {
                String sendTime = LocalDateTime.now().format(formatter);
                // Записываем передаваемую строку и время
                log("Dispatch: [" + userInput + "] in " + sendTime, logWriter);
                System.out.println("Sending: " + userInput);

                out.println(userInput);

                // Читаем ответ от сервера
                String line;
                System.out.println(" Server answer: ");
                while ((line = in.readLine()) != null) {
                    System.out.println(line);
                    String recvTime = LocalDateTime.now().format(formatter);
                    // Записываем принимаемую строку и время
                    log("Reception: [" + line + "] in " + recvTime, logWriter);
                }
                System.out.println("-------------------------");
                
                break; 
            }

            String endTime = LocalDateTime.now().format(formatter);
            // Записываем время окончания соединения
            log(" The connection is over in " + endTime + " ", logWriter);
            System.out.println("Connection closed by server.");

        } catch (IOException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    // Вспомогательный метод для записи в лог
    private static void log(String message, PrintWriter logWriter) {
        logWriter.println(message);
        System.out.println("[LOG] " + message);
    }
}