import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Client {
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;

    private JTextField inputField;
    private JTextArea log;
    private JLabel status;

    private static final String LOG_FILE = "client_log.txt";
    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private PrintWriter fileLog;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new Client().buildUI());
    }

    private void buildUI() {
        try {
            fileLog = new PrintWriter(new BufferedWriter(
                    new FileWriter(LOG_FILE, true)), true);
        } catch (IOException e) {
            System.err.println("Cannot open log file: " + e.getMessage());
        }

        JFrame frame = new JFrame("TCP Client");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        inputField = new JTextField(40);
        log = new JTextArea(20, 55);
        log.setEditable(false);
        log.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        status = new JLabel("Status: disconnected");

        JLabel hint = new JLabel(
                "Commands: connect <host> <port> | disconnect | exit    "
                + "— other input is sent to server. Press HOME to execute/send.");

        inputField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_HOME) {
                    handleInput();
                    e.consume();
                }
            }
        });

        inputField.addActionListener(e -> handleInput());

        JPanel top = new JPanel(new BorderLayout(5, 5));
        top.add(inputField, BorderLayout.NORTH);
        top.add(hint, BorderLayout.SOUTH);

        frame.getContentPane().setLayout(new BorderLayout(5, 5));
        frame.getContentPane().add(top, BorderLayout.NORTH);
        frame.getContentPane().add(new JScrollPane(log), BorderLayout.CENTER);
        frame.getContentPane().add(status, BorderLayout.SOUTH);

        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                closeEverything();
            }
        });

        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        appendLog("[CLIENT] Ready. Use 'connect <host> <port>' to connect.");
        inputField.requestFocusInWindow();
    }

    // ---------- Разбор ввода ----------

    private void handleInput() {
        String line = inputField.getText().trim();
        inputField.setText("");
        if (line.isEmpty()) return;

        String[] parts = line.split("\\s+");
        String first = parts[0].toLowerCase();

        if (first.equals("connect")) {
            handleConnect(parts);
        } else if (first.equals("disconnect")) {
            handleDisconnect();
        } else if (first.equals("exit")) {
            handleDisconnect();
            appendLog("[CLIENT] Bye.");
            closeEverything();
            System.exit(0);
        } else {
            sendData(line);
        }

        inputField.requestFocusInWindow();
    }

    // ---------- Команды ----------

    private void handleConnect(String[] parts) {
        if (socket != null && !socket.isClosed()) {
            appendLog("[CLIENT] Already connected to " + socket.getRemoteSocketAddress()
                    + ". Use 'disconnect' first.");
            return;
        }

        if (parts.length != 3) {
            appendLog("[CLIENT] Usage: connect <host> <port>");
            return;
        }

        String host = parts[1];
        int port;
        try {
            port = Integer.parseInt(parts[2]);
        } catch (NumberFormatException e) {
            appendLog("[CLIENT] Invalid port: " + parts[2]);
            return;
        }

        try {
            socket = new Socket(host, port);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(
                    new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())), true);

            appendLog("[CLIENT] Connected to " + socket);
            status.setText("Status: connected to " + socket.getRemoteSocketAddress());

            // ФАЙЛ: только время соединения
            writeFileLog("CONNECTION START: " + LocalDateTime.now().format(TIME_FMT));

            startReaderThread();

        } catch (IOException e) {
            appendLog("[CLIENT] Cannot connect: " + e.getMessage());
            socket = null;
            in = null;
            out = null;
        }
    }

    private void handleDisconnect() {
        if (socket == null || socket.isClosed()) {
            appendLog("[CLIENT] Not connected.");
            return;
        }
        try {
            // ФАЙЛ: только время разъединения
            writeFileLog("CONNECTION END:   " + LocalDateTime.now().format(TIME_FMT));

            socket.close();
            appendLog("[CLIENT] Disconnected.");
        } catch (IOException e) {
            appendLog("[CLIENT] Error on disconnect: " + e.getMessage());
        } finally {
            socket = null;
            in = null;
            out = null;
            status.setText("Status: disconnected");
        }
    }

    // ---------- Данные ----------

    private void sendData(String text) {
        if (socket == null || socket.isClosed()) {
            appendLog("[CLIENT] Not connected. Use 'connect <host> <port>' first.");
            return;
        }
        out.print(text);
        out.flush();
        appendLog("[CLIENT] Sent " + text.length() + " characters: " + text);
    }

    // ---------- Чтение ответов ----------

    private void startReaderThread() {
        final Socket currentSocket = socket;
        final BufferedReader currentIn = in;

        Thread t = new Thread(() -> {
            try {
                String response;
                while ((response = currentIn.readLine()) != null) {
                    final String r = response;
                    SwingUtilities.invokeLater(() -> appendLog("[SERVER] " + r));

                    // ФАЙЛ: только то, что вернул сервер
                    writeFileLog("SERVER: " + r);

                    if (response.equals("closing...")) {
                        SwingUtilities.invokeLater(() -> {
                            appendLog("[CLIENT] Server closed the connection.");
                            status.setText("Status: disconnected");
                        });
                        // ФАЙЛ: разъединение по инициативе сервера
                        writeFileLog("CONNECTION END:   "
                                + LocalDateTime.now().format(TIME_FMT));
                        try { currentSocket.close(); } catch (IOException ignored) {}
                        socket = null;
                        in = null;
                        out = null;
                        return;
                    }
                }
            } catch (IOException e) {
                if (currentSocket.isClosed() || socket == null) {
                    // закрытие по нашей инициативе — в файл не пишем
                } else {
                    SwingUtilities.invokeLater(() ->
                            appendLog("[CLIENT] Read error: " + e.getMessage()));
                }
            }
        });
        t.setDaemon(true);
        t.start();
    }

    // ---------- Логи ----------

    private void appendLog(String line) {
        SwingUtilities.invokeLater(() -> {
            log.append(line + "\n");
            log.setCaretPosition(log.getDocument().getLength());
        });
    }

    private synchronized void writeFileLog(String line) {
        if (fileLog == null) return;
        fileLog.println(line);
    }

    private void closeEverything() {
        if (socket != null && !socket.isClosed()) {
            writeFileLog("CONNECTION END:   " + LocalDateTime.now().format(TIME_FMT));
            try { socket.close(); } catch (IOException ignored) {}
        }
        if (fileLog != null) {
            fileLog.close();
            fileLog = null;
        }
    }
}
