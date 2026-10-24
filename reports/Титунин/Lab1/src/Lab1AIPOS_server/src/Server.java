import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.util.ArrayList;

class Pair {
    int number;
    char letter;

    Pair(char letter, int number) {
        this.letter = letter;
        this.number = number;
    }
}

public class Server {
    public static final int PORT = 8080;
    public static final int BUFFER_SIZE = 64;

    private JFrame frame;
    private JTextArea log;
    private JLabel status;
    private JButton startButton;
    private JButton stopButton;

    private ServerSocket serverSocket;
    private volatile boolean running = false;
    private Thread serverThread;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new Server().buildUI());
    }

    private void buildUI() {
        frame = new JFrame("TCP Server");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        log = new JTextArea(22, 55);
        log.setEditable(false);
        log.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));

        status = new JLabel("Status: stopped");
        startButton = new JButton("Start");
        stopButton = new JButton("Stop");
        stopButton.setEnabled(false);

        startButton.addActionListener(e -> startServer());
        stopButton.addActionListener(e -> stopServer());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttons.add(startButton);
        buttons.add(stopButton);
        buttons.add(status);

        frame.getContentPane().setLayout(new BorderLayout(5, 5));
        frame.getContentPane().add(new JScrollPane(log), BorderLayout.CENTER);
        frame.getContentPane().add(buttons, BorderLayout.SOUTH);

        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        appendLog("[SERVER] Ready. Press Start to listen on port " + PORT + ".");
    }

    private void startServer() {
        if (running) return;

        try {
            serverSocket = new ServerSocket(PORT);
        } catch (IOException e) {
            appendLog("[SERVER] Cannot open port " + PORT + ": " + e.getMessage());
            return;
        }

        running = true;
        startButton.setEnabled(false);
        stopButton.setEnabled(true);
        status.setText("Status: listening on port " + PORT);
        appendLog("[SERVER] Started: " + serverSocket);

        serverThread = new Thread(this::acceptLoop);
        serverThread.setDaemon(true);
        serverThread.start();
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                appendLog("[SERVER] Connection accepted: " + socket);

                // Каждого клиента обрабатываем в отдельном потоке,
                // чтобы сервер мог принимать нескольких сразу
                Thread clientThread = new Thread(() -> handleClient(socket));
                clientThread.setDaemon(true);
                clientThread.start();

            } catch (IOException e) {
                if (running) {
                    appendLog("[SERVER] Accept error: " + e.getMessage());
                }
                // если сокет закрыт — выходим из цикла
                if (serverSocket.isClosed()) break;
            }
        }
    }

    private void handleClient(Socket socket) {
        try {
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream()));
            PrintWriter out = new PrintWriter(
                    new BufferedWriter(
                            new OutputStreamWriter(socket.getOutputStream())), true);

            char[] buffer = new char[BUFFER_SIZE];
            int totalRead = 0;
            ArrayList<Pair> p = new ArrayList<>();

            int ch;
            while ((ch = in.read()) != -1) {
                buffer[totalRead++] = (char) ch;

                if (totalRead == BUFFER_SIZE) {
                    // Считаем вхождения
                    for (char c : buffer) {
                        boolean isExist = false;
                        for (int i = 0; i < p.size(); i++) {
                            if (p.get(i).letter == c) {
                                p.get(i).number++;
                                isExist = true;
                                break;
                            }
                        }
                        if (!isExist) {
                            p.add(new Pair(c, 1));
                        }
                    }

                    appendLog("[SERVER] Block from " + socket.getPort()
                            + ": " + new String(buffer));

                    if (p.size() <= 3) {
                        appendLog("[SERVER] -> not enough characters, closing client "
                                + socket.getPort());
                        out.println("not enough characters");
                        out.println("closing...");
                        socket.close();
                        return;
                    } else {
                        appendLog("[SERVER] -> answer:");
                        for (int i = 0; i < p.size(); i++) {
                            String line = "Character " + p.get(i).letter
                                    + ": " + p.get(i).number;
                            appendLog("[SERVER]    " + line);
                            out.println(line);
                        }
                        out.flush();
                    }

                    totalRead = 0;
                    p.clear();
                }
            }

            appendLog("[SERVER] Client " + socket.getPort() + " disconnected.");

        } catch (IOException e) {
            appendLog("[SERVER] Client error: " + e.getMessage());
        } finally {
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    private void stopServer() {
        if (!running) return;
        running = false;

        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException ignored) {}

        startButton.setEnabled(true);
        stopButton.setEnabled(false);
        status.setText("Status: stopped");
        appendLog("[SERVER] Stopped.");
    }

    private void appendLog(String line) {
        SwingUtilities.invokeLater(() -> {
            log.append(line + "\n");
            log.setCaretPosition(log.getDocument().getLength());
        });
    }
}

