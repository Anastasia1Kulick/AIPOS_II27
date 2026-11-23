import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.function.Consumer;

/**
 * Лабораторная работа №2. TCP-клиент.
 * Вариант 4: задание клиента №5 (табл. 3):
 *   - ввод символов с отправкой введённой строки по нажатию клавиши Home;
 *   - ведение файла протокола событий tcp_client.log, включающего:
 *       1) время начала и окончания соединения;
 *       3) принимаемую от сервера строку и время приёма;
 *   - разрыв соединения командой:  disconnect <адрес> <порт>;
 *   - подключение к серверу специальной командой:  connect <адрес> <порт>.
 *
 * Окно (Swing): сверху журнал обмена, снизу строка ввода. Если графики нет
 * (или запуск с ключом --console), работает консольный режим: строка уходит по Enter.
 * Запуск:  java TCPClient
 */
public class TCPClient {

    private final Consumer<String> ui;              // куда выводить сообщения пользователю
    private final PrintWriter logFile;              // файл протокола
    private final SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

    private Socket socket;
    private BufferedReader in;
    private Writer out;
    private String host;
    private int port;
    private volatile boolean connected = false;

    public TCPClient(Consumer<String> ui) throws IOException {
        this.ui = ui;
        this.logFile = new PrintWriter(new OutputStreamWriter(
                new FileOutputStream("tcp_client.log", true), StandardCharsets.UTF_8), true);
    }

    /** Подключение к серверу и запуск потока приёма. */
    public synchronized void connect(String h, int p) {
        try {
            socket = new Socket(InetAddress.getByName(h), p);
        } catch (IOException e) {
            ui.accept("Не удалось подключиться к " + h + ":" + p + " (" + e.getMessage() + ")");
            return;
        }
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            ui.accept("Ошибка потоков: " + e.getMessage());
            return;
        }
        host = socket.getInetAddress().getHostAddress();
        port = p;
        connected = true;
        log("СОЕДИНЕНИЕ НАЧАТО", host + ":" + port);
        ui.accept("Соединение с " + host + ":" + port);
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
                if (connected) closeSession("переподключение");
                connect(p[1], Integer.parseInt(p[2]));
            } catch (NumberFormatException e) {
                ui.accept("Порт должен быть числом.");
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
            disconnect(p[1], p[2]);
            return;
        }
        if (!connected) {
            ui.accept("Нет соединения. Введите: connect <адрес> <порт>");
            return;
        }
        try {
            out.write(line + "\r\n");
            out.flush();                       // без сброса буфера строка не уйдёт в сеть
            ui.accept("Вы: " + line);
        } catch (IOException e) {
            ui.accept("Ошибка отправки: " + e.getMessage());
        }
    }

    /** Разрыв соединения по команде disconnect <адрес> <порт>. */
    private void disconnect(String h, String portText) {
        if (!connected) {
            ui.accept("Нет соединения.");
            return;
        }
        try {
            boolean same = InetAddress.getByName(h).getHostAddress().equals(host)
                    && Integer.parseInt(portText) == port;
            if (!same) {
                ui.accept("Активное соединение: " + host + ":" + port);
                return;
            }
            closeSession("команда disconnect");
            ui.accept("Соединение разорвано.");
        } catch (NumberFormatException | IOException e) {
            ui.accept("Неверные параметры команды disconnect.");
        }
    }

    /** Цикл приёма строк от сервера (отдельный поток, чтобы окно не зависало). */
    private void receiveLoop() {
        final Socket mySocket = socket;                // сокет, который обслуживает этот поток
        final BufferedReader myIn = in;
        try {
            String s;
            while ((s = myIn.readLine()) != null) {      // null - сервер закрыл соединение
                if (!s.isEmpty()) {
                    log("ПОЛУЧЕНО", s);                  // п. 3 задания: принятая строка и время
                    ui.accept("Сервер: " + s);
                }
            }
        } catch (IOException e) {
            // сокет закрыт нами или оборвалась связь
        }
        synchronized (this) {
            if (connected && socket == mySocket) {      // не трогаем более новое соединение
                closeSession("закрыто сервером");
                ui.accept("Сервер закрыл соединение.");
            }
        }
    }

    /** Завершение сеанса: закрытие сокета и запись времени окончания в протокол. */
    private void closeSession(String reason) {
        connected = false;
        try { socket.close(); } catch (IOException ignored) { }
        log("СОЕДИНЕНИЕ ЗАВЕРШЕНО", host + ":" + port + " (" + reason + ")");
    }

    public synchronized void shutdown() {
        if (connected) closeSession("выход из программы");
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
        TCPClient client = new TCPClient(System.out::println);
        System.out.println("ожидание... Введите: connect <адрес> <порт>");
        BufferedReader stdIn = new BufferedReader(new InputStreamReader(System.in));
        String line;
        while ((line = stdIn.readLine()) != null) client.onInput(line);
        client.shutdown();
    }

    /** Оконный режим: строка уходит по клавише Home. */
    private static void runWindow() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JFrame frame = new JFrame("TCP-клиент (отправка строки - клавиша Home)");
            JTextArea area = new JTextArea();
            area.setEditable(false);
            area.setLineWrap(true);
            area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
            JTextField input = new JTextField();
            input.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));

            final TCPClient c;
            try {
                c = new TCPClient(msg -> SwingUtilities.invokeLater(() -> {
                    area.append(msg + "\n");
                    area.setCaretPosition(area.getDocument().getLength());
                }));
            } catch (IOException e) {
                JOptionPane.showMessageDialog(null, "Ошибка: " + e.getMessage());
                return;
            }
            // Отправка строки по нажатию клавиши Home.
            input.addKeyListener(new KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent e) {
                    if (e.getKeyCode() == KeyEvent.VK_HOME) {
                        e.consume();                        // не двигаем курсор
                        String text = input.getText();
                        input.setText("");
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
            area.append("ожидание...\nПодключение: connect 127.0.0.1 8080\n"
                    + "Разрыв: disconnect 127.0.0.1 8080\n"
                    + "Отправка введённой строки - клавиша Home.\n");
            input.requestFocusInWindow();
        });
    }
}
