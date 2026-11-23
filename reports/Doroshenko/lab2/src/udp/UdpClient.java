package udp;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Scanner;

public class UdpClient {
    private DatagramSocket socket;
    private InetAddress serverAddress;
    private int serverPort;

    private boolean connected = false;
    private static final int BUFFER_SIZE = 2048;

    public void start() {
        Scanner scanner = new Scanner(System.in);

        try {
            socket = new DatagramSocket();

            socket.setSoTimeout(3000);

            System.out.println("UDP Клиент запущен.");
            System.out.println("Команды:");
            System.out.println("  connect <адрес> <порт> — задать сервер");
            System.out.println("  disconnect             — завершить сессию");
            System.out.println("  exit                   — выход");
            System.out.println("Другие строки отправляются серверу.");

            while (true) {
                String input = scanner.nextLine();

                if (input.startsWith("connect")) {
                    String[] parts = input.split(" ");
                    if (parts.length == 3) {
                        try {
                            serverAddress = InetAddress.getByName(parts[1]);
                            serverPort = Integer.parseInt(parts[2]);
                            connected = true;
                            System.out.println("Сервер задан: " +
                                    parts[1] + ":" + serverPort);
                        } catch (Exception e) {
                            System.err.println("Ошибка: " + e.getMessage());
                        }
                    } else {
                        System.out.println("Использование: connect <адрес> <порт>");
                    }
                } else if (input.equalsIgnoreCase("disconnect")) {
                    if (connected) {
                        send("disconnect " +
                                serverAddress.getHostAddress() + " " +
                                serverPort);
                        receive();
                        connected = false;
                        System.out.println("Сессия завершена.");
                    } else {
                        System.out.println("Сначала выполните connect.");
                    }
                } else if (input.equalsIgnoreCase("exit")) {
                    break;
                } else if (connected) {
                    send(input);
                    receive();
                } else {
                    System.out.println("Сначала выполните connect <адрес> <порт>");
                }
            }
        } catch (IOException e) {
            System.err.println("Ошибка клиента: " + e.getMessage());
        } finally {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        }
    }

    private void send(String message) throws IOException {
        byte[] data = message.getBytes();
        DatagramPacket packet = new DatagramPacket(
                data, data.length, serverAddress, serverPort);
        socket.send(packet);
    }

    private void receive() {
        try {
            byte[] buf = new byte[BUFFER_SIZE];
            DatagramPacket packet = new DatagramPacket(buf, buf.length);
            socket.receive(packet);
            String response = new String(packet.getData(), 0, packet.getLength());
            System.out.println("Ответ сервера: " + response);
        } catch (java.net.SocketTimeoutException e) {
            System.out.println("(набрано <64 символов, ожидается продолжение)");
        } catch (IOException e) {
            System.err.println("Ошибка приёма: " + e.getMessage());
        }
    }
    
}