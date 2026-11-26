import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.*;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Client extends JFrame {
    private static final String LOG_FILE = "client_protocol.txt";
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private Socket socket;
    private PrintWriter writer;
    private BufferedReader reader;

    private final JTextArea logArea;
    private final JTextField inputField;

    public Client() {
        setTitle("TCP Клиент (Вариант 3 — Отправка по PgUp)");
        setSize(600, 400);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(5, 5));

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 13));
        add(new JScrollPane(logArea), BorderLayout.CENTER);

        inputField = new JTextField();
        inputField.setFont(new Font("Monospaced", Font.PLAIN, 14));
        inputField.setToolTipText("Введите текст, '..' для выхода или 'connect <адрес> <порт>' и нажмите PgUp");
        add(inputField, BorderLayout.SOUTH);

        logArea.append("Инструкция:\n");
        logArea.append("1. Подключение: connect 127.0.0.1 12345 (нажать PgUp)\n");
        logArea.append("2. Отправка текста: введите строку и нажмите PgUp\n");
        logArea.append("3. Разрыв соединения: введите .. и нажмите PgUp\n\n");

        inputField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_PAGE_UP) {
                    e.consume();
                    handleSend();
                }
            }
        });
    }

    private void handleSend() {
        String text = inputField.getText();
        if (text.isEmpty()) {
            return;
        }
        inputField.setText("");

        // Обработка команды connect <адрес> <порт>
        if (text.toLowerCase().startsWith("connect ")) {
            String[] parts = text.trim().split("\\s+");
            if (parts.length == 3) {
                String host = parts[1];
                try {
                    int port = Integer.parseInt(parts[2]);
                    connectToServer(host, port);
                } catch (NumberFormatException ex) {
                    logArea.append("[ОШИБКА] Неверный формат порта.\n");
                }
            } else {
                logArea.append("[ОШИБКА] Формат команды: connect <адрес> <порт>\n");
            }
            return;
        }

        if (socket == null || socket.isClosed() || !socket.isConnected()) {
            logArea.append("[ОШИБКА] Нет подключения к серверу. Сначала выполните connect <адрес> <порт>\n");
            return;
        }

        // Логируем передаваемую строку и время передачи
        logEvent("ПЕРЕДАЧА", text);
        writer.print(text);
        writer.flush();

        // Если отправлен маркер разрыва "..", закрываем клиентский сокет после получения ответа
        if (text.contains("..")) {
            new Thread(() -> {
                try {
                    Thread.sleep(200); // Даем время принять прощальное сообщение от сервера
                    closeConnection();
                } catch (InterruptedException ignored) {
                }
            }).start();
        }
    }

    private void connectToServer(String host, int port) {
        try {
            closeConnection();
            socket = new Socket(host, port);
            writer = new PrintWriter(socket.getOutputStream(), true);
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            logArea.append("[СИСТЕМА] Подключено к " + host + ":" + port + "\n");

            Thread receiveThread = new Thread(() -> {
                try {
                    String response;
                    while ((response = reader.readLine()) != null) {
                        logEvent("ПРИЕМ", response);
                    }
                } catch (IOException ignored) {
                } finally {
                    SwingUtilities.invokeLater(() -> logArea.append("[СИСТЕМА] Соединение закрыто.\n"));
                }
            });
            receiveThread.setDaemon(true);
            receiveThread.start();

        } catch (IOException e) {
            logArea.append("[ОШИБКА ПОДКЛЮЧЕНИЯ] " + e.getMessage() + "\n");
        }
    }

    private void closeConnection() {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException ignored) {
        }
    }

    private void logEvent(String eventType, String message) {
        String timestamp = LocalDateTime.now().format(TIME_FORMATTER);
        String logEntry = String.format("[%s] %s: %s%n", timestamp, eventType, message);

        SwingUtilities.invokeLater(() -> logArea.append(logEntry));

        try (BufferedWriter fileWriter = new BufferedWriter(new FileWriter(LOG_FILE, true))) {
            fileWriter.write(logEntry);
        } catch (IOException e) {
            SwingUtilities.invokeLater(() -> logArea.append("[ОШИБКА ЛОГА] " + e.getMessage() + "\n"));
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            Client client = new Client();
            client.setVisible(true);
            if (args.length >= 2) {
                try {
                    client.connectToServer(args[0], Integer.parseInt(args[1]));
                } catch (NumberFormatException ignored) {
                }
            }
        });
    }
}