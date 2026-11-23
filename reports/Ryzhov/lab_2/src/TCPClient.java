import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class TCPClient {
    private static final String HOST = "127.0.0.1";
    private static final int PORT = 6666;
    private static final Path LOG_FILE = Paths.get("client.log");
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private Socket socket;
    private PrintWriter writer;
    private final JFrame frame = new JFrame("TCP-клиент — вариант 9");
    private final JTextField input = new JTextField();
    private final JTextArea output = new JTextArea();

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new TCPClient().start());
    }

    private void start() {
        output.setEditable(false);
        frame.add(new JLabel("Введите load fname.txt и нажмите PgDn"), BorderLayout.NORTH);
        frame.add(new JScrollPane(output), BorderLayout.CENTER);
        frame.add(input, BorderLayout.SOUTH);

        // Отправка введённой строки только по клавише Page Down.
        input.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_DOWN, 0), "send");
        input.getActionMap().put("send", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                send();
            }
        });
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                closeConnection();
            }
        });
        frame.setSize(650, 400);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        input.requestFocusInWindow();
        input.setEnabled(false);

        // Автоматическое подключение при запуске программы.
        try {
            socket = new Socket();
            socket.connect(new InetSocketAddress(HOST, PORT), 2000);
            writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
            event("Начало соединения с " + HOST + ":" + PORT);
            input.setEnabled(true);
            input.requestFocusInWindow();
            // Отдельный поток принимает ответы, не блокируя интерфейс.
            new Thread(this::receive, "receiver").start();
        } catch (IOException e) {
            event("Ошибка подключения: " + e.getMessage());
            closeConnection();
        }
    }

    private synchronized void send() {
        String text = input.getText();
        if (text.trim().isEmpty() || writer == null || socket.isClosed()) return;
        writer.println(text);
        if (writer.checkError()) {
            event("Ошибка отправки");
            closeConnection();
            return;
        }
        event("Отправлено: " + text);
        input.setText("");
    }

    private void receive() {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                event("Получено: " + line);
            }
        } catch (IOException e) {
            if (!socket.isClosed()) event("Ошибка приёма: " + e.getMessage());
        } finally {
            closeConnection();
        }
    }

    private synchronized void closeConnection() {
        if (socket != null && !socket.isClosed()) {
            boolean connected = socket.isConnected();
            try {
                socket.close();
            } catch (IOException e) {
                event("Ошибка закрытия: " + e.getMessage());
            }
            if (connected) event("Окончание соединения");
        }
        SwingUtilities.invokeLater(() -> input.setEnabled(false));
    }

    // Записываем время соединения, отправки, приёма и отключения в файл.
    private synchronized void event(String message) {
        String entry = "[" + LocalDateTime.now().format(FORMAT) + "] " + message;
        SwingUtilities.invokeLater(() -> output.append(entry + "\n"));
        try (BufferedWriter log = Files.newBufferedWriter(LOG_FILE, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            log.write(entry);
            log.newLine();
        } catch (IOException e) {
            System.err.println("Ошибка записи журнала: " + e.getMessage());
        }
    }
}
