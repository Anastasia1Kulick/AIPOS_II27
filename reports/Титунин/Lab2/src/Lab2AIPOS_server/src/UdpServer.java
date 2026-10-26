import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

class Pair {
    int number;
    char letter;

    Pair(char letter, int number) {
        this.letter = letter;
        this.number = number;
    }
}

public class UdpServer {
    public static final int PORT = 666;
    public static final int BUFFER_SIZE = 64;

    private JFrame frame;
    private JTextArea log;
    private JLabel status;
    private JButton startButton;
    private JButton stopButton;

    private DatagramSocket socket;
    private volatile boolean running = false;
    private Thread serverThread;

    // Состояние на каждого клиента — аналог отдельного потока в TCP-версии
    private static class ClientState {
        StringBuilder buffer = new StringBuilder();
        ArrayList<Pair> pairs = new ArrayList<>();
    }

    private final Map<String, ClientState> clients = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new UdpServer().buildUI());
    }

    private void buildUI() {
        frame = new JFrame("UDP Server");
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

        appendLog("[SERVER] Ready. Press Start to listen on UDP port " + PORT + ".");
    }

    private void startServer() {
    if (running) return;

    try {
        InetAddress bindAddr = InetAddress.getByName("127.0.0.1");
        socket = new DatagramSocket(PORT, bindAddr);
    } catch (IOException e) {
        appendLog("[SERVER] Cannot open port " + PORT + ": " + e.getMessage());
        return;
    }

    running = true;
    clients.clear();
    startButton.setEnabled(false);
    stopButton.setEnabled(true);
    status.setText("Status: listening on 127.0.0.1:" + PORT);
    appendLog("[SERVER] Started: " + socket.getLocalSocketAddress());

    serverThread = new Thread(this::receiveLoop);
    serverThread.setDaemon(true);
    serverThread.start();
}

    private void receiveLoop() {
        byte[] buf = new byte[4096];
        while (running) {
            try {
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                socket.receive(packet);
                handlePacket(packet);
            } catch (IOException e) {
                if (running) {
                    appendLog("[SERVER] Receive error: " + e.getMessage());
                }
                if (socket.isClosed()) break;
            }
        }
    }

    private void handlePacket(DatagramPacket packet) {
        InetAddress addr = packet.getAddress();
        int port = packet.getPort();
        String key = addr.getHostAddress() + ":" + port;

        String msg;
        try {
            msg = new String(packet.getData(), packet.getOffset(),
                    packet.getLength(), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return;
        }

        // маркер завершения сессии от клиента (аналог EOF в TCP)
        if (msg.equals("__disconnect__")) {
            appendLog("[SERVER] Client " + port + " disconnected.");
            clients.remove(key);
            return;
        }

        ClientState state = clients.computeIfAbsent(key, k -> {
            appendLog("[SERVER] Connection accepted: " + k);
            return new ClientState();
        });

        // Накапливаем символы в буфер (как in.read() в TCP-версии)
        for (int i = 0; i < msg.length(); i++) {
            state.buffer.append(msg.charAt(i));
            if (state.buffer.length() == BUFFER_SIZE) {
                processBlock(state, addr, port);
                if (!clients.containsKey(key)) {
                    // сервер закрыл клиента внутри processBlock
                    return;
                }
            }
        }
    }

    private void processBlock(ClientState state, InetAddress addr, int port) {
        char[] buffer = state.buffer.toString().toCharArray();

        for (char c : buffer) {
            boolean isExist = false;
            for (int i = 0; i < state.pairs.size(); i++) {
                if (state.pairs.get(i).letter == c) {
                    state.pairs.get(i).number++;
                    isExist = true;
                    break;
                }
            }
            if (!isExist) {
                state.pairs.add(new Pair(c, 1));
            }
        }

        appendLog("[SERVER] Block from " + port + ": " + new String(buffer));

        if (state.pairs.size() <= 3) {
            appendLog("[SERVER] -> not enough characters, closing client " + port);
            reply(addr, port, "not enough characters");
            reply(addr, port, "closing...");
            clients.remove(addr.getHostAddress() + ":" + port);
            return;
        } else {
            appendLog("[SERVER] -> answer:");
            for (int i = 0; i < state.pairs.size(); i++) {
                String line = "Character " + state.pairs.get(i).letter
                        + ": " + state.pairs.get(i).number;
                appendLog("[SERVER]    " + line);
                reply(addr, port, line);
            }
        }

        // сброс буфера и счётчиков (как в TCP-версии)
        state.buffer.setLength(0);
        state.pairs.clear();
    }

    private void reply(InetAddress addr, int port, String text) {
        try {
            byte[] out = text.getBytes("UTF-8");
            DatagramPacket p = new DatagramPacket(out, out.length, addr, port);
            socket.send(p);
        } catch (IOException e) {
            appendLog("[SERVER] Send error: " + e.getMessage());
        }
    }

    private void stopServer() {
        if (!running) return;
        running = false;

        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (Exception ignored) {}

        clients.clear();
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
