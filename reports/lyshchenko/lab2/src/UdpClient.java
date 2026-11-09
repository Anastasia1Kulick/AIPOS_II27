import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Scanner;

public class UdpClient {
    private static final String LOG_FILE = "client_log.txt";
    private static PrintWriter logWriter;
    
    private static DatagramSocket socket;
    private static InetAddress serverAddress = null;
    private static int serverPort = -1;
    private static boolean isConnected = false;

    public static void main(String[] args) throws Exception {
        logWriter = new PrintWriter(new OutputStreamWriter(new FileOutputStream(LOG_FILE, true), StandardCharsets.UTF_8), true);
        log("1) Запуск клиента. Время: " + new Date());

        socket = new DatagramSocket();
        Scanner scanner = new Scanner(System.in);

        System.out.println(" UDP Клиент ");
        System.out.println(" Команды: ");
        System.out.println("  connect <адрес> <порт> - подключиться к серверу");
        System.out.println("  exit                   - завершить работу");
        System.out.println("  (Для отправки добавьте символ '#' в конце строки и нажмите Enter).");
        System.out.println("  Для завершения сеанса на сервере введите: ~#~");
        System.out.println();

        try {
            while (true) {
                System.out.print("> ");
                String fromUser = scanner.nextLine();
                if (fromUser == null) break;
                fromUser = fromUser.trim();

                if (fromUser.equalsIgnoreCase("exit")) {
                    break;
                }

                if (fromUser.startsWith("connect ")) {
                    String[] parts = fromUser.split("\\s+");
                    if (parts.length == 3) {
                        serverAddress = InetAddress.getByName(parts[1]);
                        serverPort = Integer.parseInt(parts[2]);
                        isConnected = true;
                        
                        log("1) Соединение установлено с " + serverAddress.getHostAddress() + ":" + serverPort + " в " + new Date());
                        System.out.println("Подключено к " + serverAddress.getHostAddress() + ":" + serverPort);
                    } else {
                        System.out.println("Ошибка формата. Используйте: connect <адрес> <порт>");
                    }
                    continue;
                }

                if (!isConnected || serverAddress == null) {
                    System.out.println("Нет активного соединения. Введите: connect <адрес> <порт>");
                    continue;
                }

                if (fromUser.equals("~#~") || fromUser.endsWith("#")) {
                    String messageToSend = fromUser.endsWith("#") ? fromUser.substring(0, fromUser.length() - 1) : fromUser;
                    
                    String packetData = messageToSend + "\n";
                    byte[] sendData = packetData.getBytes(StandardCharsets.UTF_8);

                    DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, serverAddress, serverPort);
                    socket.send(sendPacket);
                    
                    log("2) Передано серверу: \"" + messageToSend + "\" в " + new Date());
                    System.out.println("Отправлено: " + messageToSend);

                    try {
                        socket.setSoTimeout(2000);
                        byte[] receiveData = new byte[1024];
                        DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                        
                        socket.receive(receivePacket);
                        String response = new String(receivePacket.getData(), 0, receivePacket.getLength(), StandardCharsets.UTF_8);
                        
                        System.out.println("Сервер: " + response);
                        log("3) Принято от сервера: \"" + response + "\" в " + new Date());

                        if (response.contains("Сеанс завершён")) {
                            log("1) Соединение разорвано сервером (~#~) в " + new Date());
                            System.out.println("Сеанс завершён. Соединение разорвано.");
                            isConnected = false;
                            serverAddress = null;
                            serverPort = -1;
                        }
                    } catch (SocketTimeoutException e) {
                    }
                } else {
                    System.out.println("[Ожидание] Добавьте символ '#' в конце строки для отправки.");
                }
            }
        } finally {
            if (isConnected) {
                log("1) Соединение разорвано (завершение работы) в " + new Date());
            }
            log("Клиент завершил работу");
            logWriter.close();
            scanner.close();
            socket.close();
            System.out.println("Соединение закрыто. Лог сохранен в " + LOG_FILE);
        }
    }

    private static void log(String message) {
        if (logWriter != null) {
            logWriter.println(message);
        }
    }
}