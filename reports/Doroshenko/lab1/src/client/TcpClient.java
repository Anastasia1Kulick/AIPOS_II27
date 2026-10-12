package client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.file.Path;
import java.util.Scanner;

public class TcpClient {

    private static final String CONNECT_COMMAND = "connect";
    private static final String DISCONNECT_COMMAND = "disconnect";
    private static final String EXIT_COMMAND = "exit";
    private static final String LOG_FILE = "client_protocol.log";

    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private boolean connected = false;

    private EventLogger logger;

    public void start() {
        try {
            logger = new EventLogger(Path.of(LOG_FILE));
        } catch (IOException e) {
            System.err.println("Не удалось открыть файл протокола: " + e.getMessage());
            return;
        }

        printHelp();

        @SuppressWarnings("resource") // System.in закрывать не нужно
        Scanner scanner = new Scanner(System.in);

        try {
            while (true) {
                if (!scanner.hasNextLine()) {
                    break;
                }
                String input = scanner.nextLine();
                if (!handleInput(input)) {
                    break;
                }
            }
        } finally {
            disconnect();
            logger.close();
        }
    }

    private void printHelp() {
        System.out.println("TCP Клиент (вариант 5, отправка по Enter).");
        System.out.println("Команды:");
        System.out.println("  connect <адрес> <порт>  — подключиться к серверу");
        System.out.println("  disconnect              — разорвать соединение");
        System.out.println("  exit                    — выйти из клиента");
        System.out.println("Отправка строки — клавиша Enter.");
    }

    /** Возвращает false, если нужно выйти из клиента. */
    private boolean handleInput(String input) {
        if (input.startsWith(CONNECT_COMMAND)) {
            handleConnectCommand(input);
        } else if (input.startsWith(DISCONNECT_COMMAND)) {
            disconnect();
        } else if (input.equalsIgnoreCase(EXIT_COMMAND)) {
            return false;
        } else {
            sendData(input);
        }
        return true;
    }

    private void handleConnectCommand(String input) {
        String[] parts = input.split(" ");
        if (parts.length != 3) {
            System.out.println("Использование: connect <адрес> <порт>");
            return;
        }
        try {
            int port = Integer.parseInt(parts[2]);
            connect(parts[1], port);
        } catch (NumberFormatException e) {
            System.out.println("Порт должен быть числом.");
        }
    }

    private void connect(String address, int port) {
        if (connected) {
            System.out.println("Уже подключено.");
            return;
        }
        try {
            socket = new Socket(address, port);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            connected = true;

            // 1) Логируем начало соединения
            logger.logConnectionStart(address, port);
            System.out.println("Подключено к " + address + ":" + port);
        } catch (IOException e) {
            System.err.println("Не удалось подключиться: " + e.getMessage());
        }
    }

    private void sendData(String line) {
        if (!connected || out == null) {
            System.out.println("Нет подключения. Используйте 'connect <адрес> <порт>'");
            return;
        }
        out.println(line);

        try {
            String response;
            while ((response = in.readLine()) != null) {
                // 3) Логируем принимаемую строку
                logger.logIncoming(response);
                if (response.startsWith("Ошибка") || response.contains("-")) {
                    break;
                }
            }
        } catch (IOException e) {
            System.err.println("Ошибка чтения: " + e.getMessage());
            disconnect();
        }
    }

    private void disconnect() {
        if (!connected) {
            return;
        }
        try {
            if (out != null) {
                String addr = socket.getInetAddress().getHostAddress();
                int port = socket.getPort();
                out.println(DISCONNECT_COMMAND + " " + addr + " " + port);

                // 1) Логируем окончание соединения
                logger.logConnectionEnd(addr, port);
            }
            socket.close();
            connected = false;
            System.out.println("Соединение разорвано.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        new TcpClient().start();
    }
}