import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class UdpClient {
    private DatagramSocket socket;
    private InetAddress serverAddress;
    private int serverPort;

    private JTextField inputField;
    private JTextArea log;
    private JLabel status;

    private static final String LOG_FILE = "client_log.txt";
    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private PrintWriter fileLog;

    private static final int BUFFER_SIZE = 4096;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new UdpClient().buildUI());
    }

    private void buildUI() {
        try {
            fileLog = new PrintWriter(new BufferedWriter(
                    new FileWriter(LOG_FILE, true)), true);
        } catch (IOException e) {
            System.err.println("Cannot open log file: " + e.getMessage());
        }

        JFrame frame = new JFrame("UDP Client");
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

    private void handleConnect(String[] parts) {
        if (socket != null && !socket.isClosed()) {
            appendLog("[CLIENT] Already connected to "
                    + serverAddress + ":" + serverPort
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
            serverAddress = InetAddress.getByName(host);
            serverPort = port;
            socket = new DatagramSocket();

            appendLog("[CLIENT] Connected to " + serverAddress + ":" + serverPort);
            status.setText("Status: connected to " + serverAddress + ":" + serverPort);

            writeFileLog("CONNECTION START: " + LocalDateTime.now().format(TIME_FMT));

            startReaderThread();

        } catch (IOException e) {
            appendLog("[CLIENT] Cannot connect: " + e.getMessage());
            socket = null;
            serverAddress = null;
        }
    }

    private void handleDisconnect() {
    if (socket == null || socket.isClosed()) {
        appendLog("[CLIENT] Not connected.");
        socket = null;
        serverAddress = null;
        status.setText("Status: disconnected");
        return;
    }

    try {
        writeFileLog("CONNECTION END:   " + LocalDateTime.now().format(TIME_FMT));

        // 1. Отправляем маркер disconnect напрямую (без вызова sendRaw)
        try {
            byte[] buf = "__disconnect__".getBytes("UTF-8");
            socket.send(new DatagramPacket(buf, buf.length, serverAddress, serverPort));
            appendLog("[CLIENT] Disconnect marker sent.");
        } catch (IOException e) {
            appendLog("[CLIENT] Send error on disconnect: " + e.getMessage());
        }

        // 2. Закрываем сокет
        socket.close();
        appendLog("[CLIENT] Disconnected.");

    } catch (Exception e) {
        appendLog("[CLIENT] Error on disconnect: " + e.getMessage());
    } finally {
        // 3. Сбрасываем состояние в любом случае
        socket = null;
        serverAddress = null;
        status.setText("Status: disconnected");
    }
}

    private void sendData(String text) {
        if (socket == null || socket.isClosed() || serverAddress == null) {
            appendLog("[CLIENT] Not connected. Use 'connect <host> <port>' first.");
            return;
        }
        sendRaw(text);
        appendLog("[CLIENT] Sent " + text.length() + " characters: " + text);
    }

    private void sendRaw(String text) {
        try {
            byte[] buf = text.getBytes("UTF-8");
            DatagramPacket packet = new DatagramPacket(
                    buf, buf.length, serverAddress, serverPort);
            socket.send(packet);
        } catch (IOException e) {
            appendLog("[CLIENT] Send error: " + e.getMessage());
        }
    }

    private void startReaderThread() {
        final DatagramSocket currentSocket = socket;

        Thread t = new Thread(() -> {
            byte[] buf = new byte[BUFFER_SIZE];
            try {
                while (!currentSocket.isClosed()) {
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    currentSocket.receive(packet);

                    if (!packet.getAddress().equals(serverAddress)
                            || packet.getPort() != serverPort) {
                        continue;
                    }

                    String response = new String(
                            packet.getData(), packet.getOffset(),
                            packet.getLength(), "UTF-8");

                    final String r = response;
                    SwingUtilities.invokeLater(() -> appendLog("[SERVER] " + r));

                    writeFileLog("SERVER: " + r);

                    if (response.equals("closing...")) {
                        SwingUtilities.invokeLater(() -> {
                            appendLog("[CLIENT] Server closed the connection.");
                            status.setText("Status: disconnected");
                        });
                        writeFileLog("CONNECTION END:   "
                                + LocalDateTime.now().format(TIME_FMT));
                        try { currentSocket.close(); } catch (Exception ignored) {}
                        socket = null;
                        serverAddress = null;
                        return;
                    }
                }
            } catch (IOException e) {
                if (currentSocket.isClosed() || socket == null) {
                    // закрытие по нашей инициативе
                } else {
                    SwingUtilities.invokeLater(() ->
                            appendLog("[CLIENT] Read error: " + e.getMessage()));
                }
            }
        });
        t.setDaemon(true);
        t.start();
    }

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
            try { socket.close(); } catch (Exception ignored) {}
        }
        if (fileLog != null) {
            fileLog.close();
            fileLog = null;
        }
    }
}
