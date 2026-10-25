import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

public class TcpClient extends JFrame {
    static final String DEFAULT_HOST = "127.0.0.1";
    static final int DEFAULT_PORT = 8080;

    private final JTextArea area = new JTextArea();
    private final JTextField field = new JTextField();
    private final ClientLog log = new ClientLog("tcp_client.log");
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private volatile Socket socket;
    private volatile PrintWriter out;

    public TcpClient(final String host, final int port) {
        super("TCP client, variant 8");
        Font font = new Font(Font.MONOSPACED, Font.PLAIN, 14);
        area.setEditable(false);
        area.setFont(font);
        field.setFont(font);
        JLabel hint = new JLabel("Type a line and press Home to send. Command: disconnect <address> <port>");
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(hint, BorderLayout.NORTH);
        bottom.add(field, BorderLayout.CENTER);
        add(new JScrollPane(area), BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);
        setSize(680, 440);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                shutdown();
            }
        });
        field.addKeyListener(new KeyAdapter() {
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_HOME) {
                    e.consume();
                    sendLine();
                }
            }
        });
        setVisible(true);
        field.requestFocusInWindow();
        Thread t = new Thread(new Runnable() {
            public void run() {
                connect(host, port);
            }
        });
        t.setDaemon(true);
        t.start();
    }

    private void show(final String text) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                area.append(text + "\n");
                area.setCaretPosition(area.getDocument().getLength());
            }
        });
    }

    private void setInputEnabled(final boolean enabled) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                field.setEnabled(enabled);
            }
        });
    }

    private void connect(String host, int port) {
        BufferedReader in;
        try {
            Socket s = new Socket(host, port);
            in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
            out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8)), true);
            socket = s;
            connected.set(true);
        } catch (IOException e) {
            show("Cannot connect to " + host + ":" + port + " (" + e.getMessage() + ")");
            log.info("CONNECTION FAILED " + host + ":" + port);
            setInputEnabled(false);
            return;
        }
        log.info("CONNECTION STARTED " + socket.getRemoteSocketAddress());
        show("Connected to " + socket.getRemoteSocketAddress());
        try {
            String line;
            while ((line = in.readLine()) != null) {
                show("Server: " + line);
            }
            closeConnection("closed by server");
        } catch (IOException e) {
            closeConnection("connection lost");
        }
    }

    private void closeConnection(String reason) {
        if (!connected.compareAndSet(true, false)) {
            return;
        }
        try {
            socket.close();
        } catch (IOException e) {
            show("Socket not closed");
        }
        log.info("CONNECTION ENDED (" + reason + ")");
        show("Connection ended: " + reason);
        setInputEnabled(false);
    }

    private void sendLine() {
        String text = field.getText();
        if (text.trim().isEmpty()) {
            return;
        }
        field.setText("");
        String[] tokens = text.trim().split("\\s+");
        if (tokens[0].equalsIgnoreCase("disconnect")) {
            disconnect(tokens);
            return;
        }
        if (!connected.get()) {
            show("Not connected");
            return;
        }
        out.println(text);
        if (out.checkError()) {
            closeConnection("send error");
            return;
        }
        log.info("SENT: " + text);
        show("You: " + text);
    }

    private void disconnect(String[] tokens) {
        if (tokens.length != 3) {
            show("Usage: disconnect <address> <port>");
            return;
        }
        try {
            InetAddress address = InetAddress.getByName(tokens[1]);
            int port = Integer.parseInt(tokens[2]);
            if (connected.get() && address.equals(socket.getInetAddress()) && port == socket.getPort()) {
                closeConnection("disconnect command");
            } else {
                show("No connection to " + tokens[1] + ":" + tokens[2]);
            }
        } catch (UnknownHostException e) {
            show("Unknown host: " + tokens[1]);
        } catch (NumberFormatException e) {
            show("Bad port: " + tokens[2]);
        }
    }

    private void shutdown() {
        closeConnection("client closed");
        log.close();
        dispose();
        System.exit(0);
    }

    public static void main(String[] args) {
        final String host = args.length > 0 ? args[0] : DEFAULT_HOST;
        final int port = args.length > 1 ? Integer.parseInt(args[1]) : DEFAULT_PORT;
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                new TcpClient(host, port);
            }
        });
    }
}
