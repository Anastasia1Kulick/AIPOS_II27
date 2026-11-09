import java.io.*;
import java.net.*;
import java.util.Scanner;
import java.util.logging.*;

public class TaskClient {
    private static final Logger logger = Logger.getLogger(TaskClient.class.getName());
    private static Socket socket;
    private static PrintWriter out;
    private static BufferedReader in;
    private static boolean isConnected = false;

    public static void main(String[] args) {
        setupLogger("client_log.txt");
        Scanner scanner = new Scanner(System.in);
        
        System.out.println("Клиент запущен.");
        System.out.println("Введите 'connect <IP> <PORT>' для подключения.");
        System.out.println("Введите 'disconnect' для отключения.");

        while (true) {
            System.out.print("> ");
            String command = scanner.nextLine();

            if (command.startsWith("connect ")) {
                String[] parts = command.split(" ");
                if (parts.length == 3) {
                    try {
                        connect(parts[1], Integer.parseInt(parts[2]));
                    } catch (NumberFormatException e) {
                        System.out.println("Неверный формат порта.");
                    }
                } else {
                    System.out.println("Формат: connect <IP> <PORT>");
                }
            } else if (command.equalsIgnoreCase("disconnect")) {
                disconnect();
            } else if (isConnected) {
                sendData(command);
            } else {
                System.out.println("Соединение не установлено. Используйте 'connect <IP> <PORT>'.");
            }
        }
    }

    private static void connect(String ip, int port) {
        try {
            socket = new Socket(ip, port);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            isConnected = true;
            logger.info("Успешное подключение к " + ip + ":" + port);
            System.out.println("Соединение установлено.");

            new Thread(() -> {
                try {
                    String response;
                    while ((response = in.readLine()) != null) {
                        System.out.println("Сервер: " + response);
                        logger.info("Получено от сервера: " + response);
                        
                        if (response.startsWith("SESSION_END")) {
                            disconnect();
                            break;
                        }
                    }
                } catch (IOException e) {
                    if (isConnected) {
                        System.out.println("Соединение потеряно.");
                        isConnected = false;
                    }
                }
            }).start();
        } catch (IOException e) {
            System.out.println("Ошибка подключения: " + e.getMessage());
            logger.warning("Ошибка подключения: " + e.getMessage());
        }
    }

    private static void sendData(String data) {
        out.println(data);
        logger.info("Отправлено серверу: " + data);
    }

    private static void disconnect() {
        if (isConnected) {
            try {
                socket.close();
            } catch (IOException e) { /* ignore */ }
            isConnected = false;
            System.out.println("Соединение разорвано.");
            logger.info("Соединение разорвано пользователем или сервером.");
        } else {
            System.out.println("Соединение уже разорвано.");
        }
    }

    private static void setupLogger(String fileName) {
    try {
        FileHandler fh = new FileHandler(fileName, true);
        fh.setEncoding("UTF-8");  
        fh.setFormatter(new SimpleFormatter());
        logger.addHandler(fh);
        logger.setUseParentHandlers(false);
    } catch (IOException e) {
        e.printStackTrace();
    }
  }
}