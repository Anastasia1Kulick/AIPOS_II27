import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.function.Consumer;

/**
 * Лабораторная работа №3. UDP-клиент.
 * Вариант 4: задание клиента №5 (табл. 3):
 *   - ввод символов с отправкой введённой строки по нажатию клавиши Home;
 *   - ведение файла протокола событий client.log, включающего:
 *       1) время начала и окончания соединения;
 *       3) принимаемую от сервера строку и время приёма;
 *   - разрыв соединения командой:  disconnect <адрес> <порт>;
 *   - подключение к серверу специальной командой:  connect <адрес> <порт>.
 *
 * Окно (Swing): сверху - журнал обмена, снизу - строка ввода.
 * Если графики нет (или запуск с ключом --console), работает консольный режим,
 * где строка отправляется по Enter (консоль не умеет ловить клавишу Home).
 */
public class UDPClient {

    /** Порт сервера, к которому подключён клиент (заполняется командой connect). */
    public static int PORT;

    /** Метка сервера: "соединение" закрыто сервером. */
    static final String BYE = "[BYE]";

    private final DatagramSocket socket;      // один сокет на всё время работы
    private final Consumer<String> ui;        // куда выводить сообщения пользователю
    private final PrintWriter logFile;        // файл протокола
    private final SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

    private InetAddress serverAddress;        // адрес сервера (после connect)
    private volatile boolean connected = false;

    public UDPClient(Consumer<String> ui) throws IOException {
        this.ui = ui;
        this.socket = new DatagramSocket();   // свободный локальный порт выберет система
        this.logFile = new PrintWriter(new OutputStreamWriter(
                new FileOutputStream("client.log", true), StandardCharsets.UTF_8), true);
        // Поток приёма: ждёт датаграммы от сервера независимо от ввода пользователя.
        Thread receiver = new Thread(this::receiveLoop, "receiver");
        receiver.setDaemon(true);
        receiver.start();
    }

    /** Обработка строки, введённой пользователем. */
    public synchronized void onInput(String line) {
        line = line.trim();
        if (line.isEmpty()) return;

        // Специальная команда подключения: connect <адрес> <порт>
        if (line.startsWith("connect")) {
            String[] p = line.split("\\s+");
            if (p.length != 3) {
                ui.accept("Формат команды: connect <адрес> <порт>");
                return;
            }
            try {
                connectTo(p[1], Integer.parseInt(p[2]));
            } catch (NumberFormatException e) {
                ui.accept("Порт должен быть числом от 1 до 65535.");
            } catch (IOException e) {
                ui.accept("Не удалось подключиться: " + e.getMessage());
            }
            return;
        }
        // Команда разрыва соединения: disconnect <адрес> <порт>
        if (line.startsWith("disconnect")) {
            String[] p = line.split("\\s+");
            if (p.length != 3) {
                ui.accept("Формат команды: disconnect <адрес> <порт>");
                return;
            }
            disconnectFrom(p[1], p[2]);
            return;
        }
        if (!connected) {
            ui.accept("Нет соединения. Введите: connect <адрес> <порт>");
            return;
        }
        try {
            send(line);
        } catch (IOException e) {
            ui.accept("Ошибка отправки: " + e.getMessage());
        }
    }

    /** "Подключение": запоминаем адрес сервера и отправляем приветственную датаграмму. */
    private void connectTo(String host, int port) throws IOException {
        if (port < 1 || port > 65535) throw new NumberFormatException();
        if (connected) closeSession("переподключение");
        serverAddress = InetAddress.getByName(host);
        PORT = port;
        connected = true;
        log("СОЕДИНЕНИЕ НАЧАТО", serverAddress.getHostAddress() + ":" + PORT);
        ui.accept("Соединение с " + serverAddress.getHostAddress() + ":" + PORT);
        rawSend("connect");
    }

    /** Разрыв соединения: сообщаем серверу (END) и пишем время окончания в протокол. */
    private void disconnectFrom(String host, String portText) {
        if (!connected) {
            ui.accept("Нет соединения.");
            return;
        }
        try {
            boolean same = InetAddress.getByName(host).equals(serverAddress)
                    && Integer.parseInt(portText) == PORT;
            if (!same) {
                ui.accept("Активное соединение: " + serverAddress.getHostAddress() + ":" + PORT);
                return;
            }
            rawSend("END");
            closeSession("команда disconnect");
            ui.accept("Соединение разорвано.");
        } catch (NumberFormatException | IOException e) {
            ui.accept("Неверные параметры команды disconnect.");
        }
    }

    /** Отправка строки серверу (в протокол не пишется - по заданию логируются только п. 1 и 3). */
    private void send(String text) throws IOException {
        rawSend(text);
        ui.accept("Вы: " + text);
    }

    private void rawSend(String text) throws IOException {
        byte[] data = text.getBytes(StandardCharsets.UTF_8);
        if (data.length > 1000) throw new IOException("слишком длинная строка (больше 1000 байт)");
        DatagramPacket datagram = new DatagramPacket(data, data.length, serverAddress, PORT);
        socket.send(datagram);
    }

    /** Цикл приёма датаграмм от сервера (работает в отдельном потоке). */
    private void receiveLoop() {
        while (!socket.isClosed()) {
            try {
                byte[] buf = new byte[2048];
                DatagramPacket datagram = new DatagramPacket(buf, buf.length);
                socket.receive(datagram);   // ждём ответ сервера
                // Используем только принятые байты (getLength), а не весь буфер.
                String received = new String(datagram.getData(), 0,
                        datagram.getLength(), StandardCharsets.UTF_8);
                boolean bye = received.startsWith(BYE);
                if (bye) received = received.substring(BYE.length()).trim();
                log("ПОЛУЧЕНО", received);
                ui.accept("Сервер: " + received);
                if (bye) {
                    synchronized (this) {
                        if (connected) closeSession("закрыто сервером");
                    }
                    ui.accept("Соединение закрыто. Для работы введите: connect <адрес> <порт>");
                }
            } catch (IOException e) {
                if (!socket.isClosed()) ui.accept("Ошибка приёма: " + e.getMessage());
            }
        }
    }

    /** Завершение сеанса: запись времени окончания соединения в протокол. */
    private void closeSession(String reason) {
        connected = false;
        log("СОЕДИНЕНИЕ ЗАВЕРШЕНО", serverAddress.getHostAddress() + ":" + PORT + " (" + reason + ")");
    }

    /** Корректное закрытие клиента. */
    public synchronized void shutdown() {
        if (connected) {
            try { rawSend("END"); } catch (IOException ignored) { }
            closeSession("выход из программы");
        }
        socket.close();
        logFile.close();
    }

    /** Строка протокола: время | событие | данные. */
    private synchronized void log(String event, String data) {
        logFile.println(fmt.format(new Date()) + " | " + event + " | "
                + data.replace("\r", "").replace("\n", "\\n"));
    }

    // ---------------------------------------------------------------- запуск

    public static void main(String[] args) throws Exception {
        boolean console = GraphicsEnvironment.isHeadless()
                || (args.length > 0 && args[0].equals("--console"));
        if (console) runConsole(); else runWindow();
    }

    /** Консольный режим: строка уходит по Enter. */
    private static void runConsole() throws IOException {
        UDPClient client = new UDPClient(msg -> System.out.println(msg));
        System.out.println("ожидание... Введите: connect <адрес> <порт>");
        BufferedReader stdIn = new BufferedReader(new InputStreamReader(System.in));
        String line;
        while ((line = stdIn.readLine()) != null) {
            client.onInput(line);
        }
        client.shutdown();
    }

    /** Оконный режим: строка уходит по клавише Home. */
    private static void runWindow() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JFrame frame = new JFrame("UDP-клиент (отправка строки - клавиша Home)");
            JTextArea area = new JTextArea();
            area.setEditable(false);
            area.setLineWrap(true);
            area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
            JTextField input = new JTextField();
            input.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));

            UDPClient client;
            try {
                client = new UDPClient(msg -> SwingUtilities.invokeLater(() -> {
                    area.append(msg + "\n");
                    area.setCaretPosition(area.getDocument().getLength());
                }));
            } catch (IOException e) {
                JOptionPane.showMessageDialog(null, "Ошибка: " + e.getMessage());
                return;
            }
            final UDPClient c = client;

            // Отправка строки по нажатию клавиши Home.
            input.addKeyListener(new KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent e) {
                    if (e.getKeyCode() == KeyEvent.VK_HOME) {
                        e.consume();                        // не двигаем курсор
                        String text = input.getText();
                        input.setText("");
                        // отправку делаем вне потока интерфейса, чтобы окно не "зависало"
                        new Thread(() -> c.onInput(text)).start();
                    }
                }
            });
            frame.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    c.shutdown();
                    frame.dispose();
                    System.exit(0);
                }
            });
            frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
            frame.add(new JScrollPane(area), BorderLayout.CENTER);
            frame.add(input, BorderLayout.SOUTH);
            frame.setSize(700, 450);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
            area.append("ожидание...\nПодключение: connect 127.0.0.1 666\n"
                    + "Разрыв: disconnect 127.0.0.1 666\n"
                    + "Отправка введённой строки - клавиша Home.\n");
            input.requestFocusInWindow();
        });
    }
}
