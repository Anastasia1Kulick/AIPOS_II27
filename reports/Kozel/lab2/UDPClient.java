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

public class UDPClient {
    private static final String SERVER_IP = "127.0.0.1";
    private static final int SERVER_PORT = 666;

    private static DatagramSocket socket;
    private static InetAddress serverAddress;
    private static JTextArea inputArea;
    private static JTextArea outputArea;
    private static Logger logger;

    public static void main(String[] args) {
        // Logger setup
        setupLogger();
        logger.info("=== Session started (connecting to server) ===");

        // Network initialization (Automatic connection)
        try {
            socket = new DatagramSocket();
            serverAddress = InetAddress.getByName(SERVER_IP);
            logger.info("Auto connection to " + SERVER_IP + ":" + SERVER_PORT + " done.");

            // Start thread for receiving server responses
            new Thread(UDPClient::receiveLoop).start();
        } catch (Exception e) {
            logger.severe("Network init error: " + e.getMessage());
            return;
        }

        // Create GUI
        SwingUtilities.invokeLater(UDPClient::createGUI);
    }

    private static void setupLogger() {
        logger = Logger.getLogger("UDPClientLog");
        logger.setUseParentHandlers(false);
        try {
            // Log file will be created in the launch directory
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
        frame.setSize(600, 400);
        frame.setLayout(new BorderLayout());

        inputArea = new JTextArea();
        inputArea.setLineWrap(true);
        inputArea.setWrapStyleWord(true);
        JScrollPane inputScroll = new JScrollPane(inputArea);
        inputScroll.setBorder(BorderFactory.createTitledBorder("Input"));

        outputArea = new JTextArea();
        outputArea.setEditable(false);
        outputArea.setLineWrap(true);
        JScrollPane outputScroll = new JScrollPane(outputArea);
        outputScroll.setBorder(BorderFactory.createTitledBorder("Server responses"));

        // --- FIX: Use Key Bindings instead of KeyListener ---
        InputMap inputMap = inputArea.getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap actionMap = inputArea.getActionMap();

        // Bind Page Down key to "sendAction"
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "sendAction");

        // Define the action
        actionMap.put("sendAction", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String text = inputArea.getText();
                if (!text.trim().isEmpty()) {
                    sendToServer(text);
                    inputArea.setText(""); 
                }
            }
        });

        frame.add(inputScroll, BorderLayout.NORTH);
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

    private static void sendToServer(String text) {
        try {
            byte[] sendData = text.getBytes();
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, serverAddress, SERVER_PORT);
            socket.send(sendPacket);

            String time = new SimpleDateFormat("HH:mm:ss").format(new Date());
            logger.info("[SENT at " + time + "] -> " + text);

            SwingUtilities.invokeLater(() ->
                outputArea.append("[" + time + "] You sent: " + text + "\n"));
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

                // Update GUI from background thread
                SwingUtilities.invokeLater(() -> {
                    outputArea.append("[" + time + "] Server: " + response + "\n");

                    // If server reported session end, disable input
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