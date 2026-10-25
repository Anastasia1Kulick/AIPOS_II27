import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;

public class UDPClient {
    private static DatagramSocket socket = null;
    private static InetAddress serverAddress = null;
    private static int serverPort = -1;
    private static Thread listenerThread = null;
    private static boolean isRunning = true;

    private static final SimpleDateFormat SDF = new SimpleDateFormat("HH:mm:ss.SSS");
    private static final String LOG_FILE = "file.log";

    public static void main(String[] args) {
        System.out.println("connect <address> <port>");
        System.out.println("disconnect <address> <port>");
        System.out.println("exit");

        BufferedReader consoleReader = new BufferedReader(new InputStreamReader(System.in));

        try {
            while (isRunning) {
                System.out.print("> ");
                String input = consoleReader.readLine();
                if (input == null) break;
                String trimmed = input.trim();
                if (trimmed.isEmpty()) continue;

                if (trimmed.equalsIgnoreCase("exit")) {
                    if (socket != null && !socket.isClosed()) socket.close();
                    break;
                }

                if (trimmed.startsWith("connect ")) {
                    String[] parts = trimmed.split("\\s+");
                    if (parts.length == 3) connect(parts[1], parts[2]);
                    else System.out.println("Формат: connect <адрес> <порт>");
                } else if (trimmed.startsWith("disconnect ")) {
                    String[] parts = trimmed.split("\\s+");
                    if (parts.length == 3) disconnect(parts[1], parts[2]);
                    else System.out.println("Формат: disconnect <адрес> <порт>");
                } else {
                    sendText(input);
                }
            }
        } catch (IOException e) {
            System.err.println("Ошибка ввода-вывода: " + e.getMessage());
        }
    }

    private static void connect(String host, String portStr) {
        try {
            InetAddress addr = InetAddress.getByName(host);
            int port = Integer.parseInt(portStr);

            if (socket != null && !socket.isClosed()) socket.close();

            serverAddress = addr;
            serverPort = port;
            socket = new DatagramSocket();

            listenerThread = new Thread(UDPClient::receiveLoop);
            listenerThread.setDaemon(true);
            listenerThread.start();

            logToFile("Установлено соединение с " + addr.getHostAddress() + ":" + port);
            System.out.println("Подключено к " + addr.getHostAddress() + ":" + port);
        } catch (Exception e) {
            logToFile("Ошибка подключения: " + e.getMessage());
            System.err.println("Ошибка подключения: " + e.getMessage());
        }
    }

    private static void disconnect(String host, String portStr) {
        try {
            int port = Integer.parseInt(portStr);
            if (serverAddress == null || serverPort != port ||
                    !serverAddress.getHostAddress().equals(InetAddress.getByName(host).getHostAddress())) {
                System.out.println("Указанный адрес/порт не совпадает с текущим соединением.");
                return;
            }
            logToFile("Разрыв соединения с " + host + ":" + port);
            System.out.println("Разрыв соединения с " + host + ":" + port);
            if (socket != null && !socket.isClosed()) socket.close();
            serverAddress = null;
            serverPort = -1;
        } catch (Exception e) {
            logToFile("Ошибка разрыва: " + e.getMessage());
            System.err.println("Ошибка разрыва: " + e.getMessage());
        }
    }

    private static void sendText(String text) {
        if (serverAddress == null || serverPort <= 0 || socket == null || socket.isClosed()) {
            System.out.println("Сначала подключитесь: connect <адрес> <порт>");
            return;
        }
        try {
            byte[] data = text.getBytes(StandardCharsets.UTF_8);
            DatagramPacket packet = new DatagramPacket(data, data.length, serverAddress, serverPort);
            socket.send(packet);
            logToFile("Отправлено (" + data.length + " б): " + text);
            System.out.println("Отправлено (" + data.length + " б): " + text);
        } catch (IOException e) {
            logToFile("Ошибка отправки: " + e.getMessage());
            System.err.println("Ошибка отправки: " + e.getMessage());
        }
    }

    private static void receiveLoop() {
        byte[] buf = new byte[2048];
        while (true) {
            try {
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                socket.receive(packet);
                String response = new String(packet.getData(), packet.getOffset(), packet.getLength(), StandardCharsets.UTF_8);
                String clean = response.replace("\n", " | ").trim();
                logToFile("Принято: " + clean);
                System.out.println("\n[Ответ] " + clean);
            } catch (SocketException e) {
                break;
            } catch (IOException e) {
                if (socket != null && !socket.isClosed()) {
                    logToFile("Ошибка приёма: " + e.getMessage());
                }
                break;
            }
        }
    }

    private static synchronized void logToFile(String msg) {
        String time = SDF.format(new Date());
        String entry = "[" + time + "] " + msg;
        try (PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(LOG_FILE, true)))) {
            out.println(entry);
        } catch (IOException e) {
            System.err.println("Ошибка записи в лог: " + e.getMessage());
        }
    }
}