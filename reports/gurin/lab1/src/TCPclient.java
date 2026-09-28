import java.io.*;
import java.net.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Scanner;

public class TCPclient {
    private static final String LOG_FILE = "client.log";
    private static final DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        Socket socket = null;
        BufferedReader in = null;
        PrintWriter out = null;

        try {
            System.out.println("Введите команду: connect <host> <port>");
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine().trim();
                if (line.startsWith("connect ")) {
                    String[] parts = line.split("\\s+");
                    if (parts.length == 3) {
                        String host = parts[1];
                        int port = Integer.parseInt(parts[2]);
                        socket = new Socket(host, port);
                        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                        out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())), true);
                        System.out.println("Подключено к " + host + ":" + port);
                        break;
                    } else {
                        System.out.println("Использование: connect <host> <port>");
                    }
                } else {
                    System.out.println("Неизвестная команда. Используйте: connect <host> <port>");
                }
            }

            if (socket != null) {
                System.out.println("Вводите строки для отправки (пустая строка — выход):");
                while (scanner.hasNextLine()) {
                    String msg = scanner.nextLine();
                    if (msg.isEmpty()) break;

                    
                    log("Sent: " + msg + " at " + LocalDateTime.now().format(fmt));

                    out.println(msg);               
                    String response = in.readLine(); 
                    if (response == null) {
                        System.out.println("Сервер закрыл соединение.");
                        break;
                    }
                    System.out.println("Ответ сервера: " + response);
                }
            }
        } catch (IOException e) {
            System.err.println("Ошибка клиента: " + e);
        } finally {
            try {
                if (socket != null) socket.close();
                if (scanner != null) scanner.close();
            } catch (IOException e) { /* ignore */ }
        }
    }

    private static void log(String message) {
        try (FileWriter fw = new FileWriter(LOG_FILE, true);
             BufferedWriter bw = new BufferedWriter(fw);
             PrintWriter out = new PrintWriter(bw)) {
            out.println(message);
        } catch (IOException e) {
            System.err.println("Не удалось записать в лог: " + e);
        }
    }
}
