import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.*;
import javax.swing.*;
import javax.swing.text.*;

public class UDPClient {
    private static final String SERVER_IP = "127.0.0.1";
    private static final int SERVER_PORT = 666;
    private static final int MAX_MESSAGE_LENGTH = 64; 

    private static DatagramSocket socket;
    private static InetAddress serverAddress;
    private static JTextArea inputArea;
    private static JTextArea outputArea;
    private static JLabel statusLabel; 
    private static Logger logger;

    public static void main(String[] args) {
        setupLogger();
        logger.info("=== Session started (connecting to server) ===");

        try {
            socket = new DatagramSocket();
            serverAddress = InetAddress.getByName(SERVER_IP);
            logger.info("Auto connection to " + SERVER_IP + ":" + SERVER_PORT + " done.");
            new Thread(UDPClient::receiveLoop).start();
        } catch (Exception e) {
            logger.severe("Network init error: " + e.getMessage());
            return;
        }

        SwingUtilities.invokeLater(UDPClient::createGUI);
    }

    private static void setupLogger() {
        logger = Logger.getLogger("UDPClientLog");
        logger.setUseParentHandlers(false);
        try {
            FileHandler fh = new FileHandler("client_log.txt", true);
            fh.setFormatter(new SimpleFormatter());
            logger.addHandler(fh);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void createGUI() {
        JFrame frame = new JFrame("UDP Client (Variant 5)");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(600, 450);
        frame.setLayout(new BorderLayout());

    
        JPanel topPanel = new JPanel(new BorderLayout());

        inputArea = new JTextArea(4, 40);
        inputArea.setLineWrap(true);
        inputArea.setWrapStyleWord(true);


        ((AbstractDocument) inputArea.getDocument()).setDocumentFilter(new DocumentFilter() {
            @Override
            public void insertString(FilterBypass fb, int offset, String text, AttributeSet attr)
                    throws BadLocationException {
                int currentLength = fb.getDocument().getLength();
                int newLength = currentLength + text.length();
                if (newLength <= MAX_MESSAGE_LENGTH) {
                    super.insertString(fb, offset, text, attr);
                    updateCharCounter();
                }
            }

            @Override
            public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs)
                    throws BadLocationException {
                int currentLength = fb.getDocument().getLength();
                int newLength = currentLength - length + text.length();
                if (newLength <= MAX_MESSAGE_LENGTH) {
                    super.replace(fb, offset, length, text, attrs);
                    updateCharCounter();
                }
            }
        });

        JScrollPane inputScroll = new JScrollPane(inputArea);
        inputScroll.setBorder(BorderFactory.createTitledBorder(
                "Input (max " + MAX_MESSAGE_LENGTH + " symbols)"));

        // Счетчик символов
        statusLabel = new JLabel("Symbols: 0 / " + MAX_MESSAGE_LENGTH);
        statusLabel.setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 5));

        topPanel.add(inputScroll, BorderLayout.CENTER);
        topPanel.add(statusLabel, BorderLayout.SOUTH);


        outputArea = new JTextArea();
        outputArea.setEditable(false);
        outputArea.setLineWrap(true);
        JScrollPane outputScroll = new JScrollPane(outputArea);
        outputScroll.setBorder(BorderFactory.createTitledBorder("Server responses"));


        InputMap inputMap = inputArea.getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap actionMap = inputArea.getActionMap();

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "sendAction");

        actionMap.put("sendAction", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String text = inputArea.getText();
                if (!text.trim().isEmpty()) {
                    sendToServer(text);
                    inputArea.setText("");
                    updateCharCounter();
                }
            }
        });

        frame.add(topPanel, BorderLayout.NORTH);
        frame.add(outputScroll, BorderLayout.CENTER);

        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                logger.info("=== Session ended (client closed) ===");
                if (socket != null) socket.close();
            }
        });

        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        inputArea.requestFocus();
    }

    // Обновление счетчика символов в статусной строке
    private static void updateCharCounter() {
        if (statusLabel != null) {
            int len = inputArea.getText().length();
            statusLabel.setText("Symbols: " + len + " / " + MAX_MESSAGE_LENGTH);
            // Подсветка красным, если достигнут лимит
            if (len >= MAX_MESSAGE_LENGTH) {
                statusLabel.setForeground(Color.RED);
            } else {
                statusLabel.setForeground(Color.BLACK);
            }
        }
    }

    private static void sendToServer(String text) {
        try {
            byte[] sendData = text.getBytes();
            DatagramPacket sendPacket = new DatagramPacket(
                    sendData, sendData.length, serverAddress, SERVER_PORT);
            socket.send(sendPacket);

            String time = new SimpleDateFormat("HH:mm:ss").format(new Date());
            logger.info("[SENT at " + time + "] (" + text.length() + " symbols) -> " + text);

            SwingUtilities.invokeLater(() ->
                outputArea.append("[" + time + "] You sent (" + text.length() + " symbols): " + text + "\n"));
        } catch (IOException e) {
            logger.severe("Send error: " + e.getMessage());
        }
    }

    private static void receiveLoop() {
        byte[] receiveBuffer = new byte[1024];
        while (true) {
            try {
                DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
                socket.receive(receivePacket);
                String response = new String(receivePacket.getData(), 0, receivePacket.getLength());

                String time = new SimpleDateFormat("HH:mm:ss").format(new Date());
                logger.info("[RECEIVED at " + time + "] <- " + response);

                SwingUtilities.invokeLater(() -> {
                    outputArea.append("[" + time + "] Server: " + response + "\n");

                    if (response.contains("Session terminated")) {
                        inputArea.setEnabled(false);
                        outputArea.append("--- Connection closed by server ---\n");
                    }
                });
            } catch (IOException e) {
                if (socket.isClosed()) break;
            }
        }
    }
}