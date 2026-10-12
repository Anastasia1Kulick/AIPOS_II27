import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 * TCP-клиент задания 2.
 * Соединение открывает команда {@code connect <адрес> <порт>}.
 * Набранная строка уходит на сервер по PgUp.
 * В client.log пишутся начало и конец соединения и строки, принятые от сервера.
 */
public final class FileClient {

    private static final Logger LOG = Logger.getLogger(FileClient.class.getName());

    public static void main(String[] args) throws Exception {
        attachLog("client.log");
        try (RawConsole console = new RawConsole()) {
            System.out.println("Подключение: connect <адрес> <порт>");
            System.out.println(console.lineMode()
                    ? "Отправка строки: Enter. Выход: exit"
                    : "Отправка строки: PgUp. Выход: exit");
            Session session = new Session();
            String line;
            while ((line = console.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                if (line.equalsIgnoreCase("exit")) {
                    break;
                }
                if (line.startsWith("disconnect ")) {
                    System.out.println("Команда disconnect в этом варианте не используется.");
                    continue;
                }
                if (line.startsWith("connect ")) {
                    try {
                        session.connect(line);
                    } catch (IOException exception) {
                        System.out.println("Не удалось подключиться: " + exception.getMessage());
                    }
                    continue;
                }
                // Отправленную строку в client.log не пишем: в задании только события 1 и 3.
                session.send(line);
            }
            session.close();
        }
    }

    private static void attachLog(String fileName) throws IOException {
        System.setProperty("java.util.logging.SimpleFormatter.format", "[%1$tF %1$tT] %5$s%n");
        Logger root = Logger.getLogger("");
        for (var handler : root.getHandlers()) {
            root.removeHandler(handler);
        }
        FileHandler fileHandler = new FileHandler(fileName, true);
        fileHandler.setFormatter(new SimpleFormatter());
        ConsoleHandler console = new ConsoleHandler();
        console.setFormatter(new SimpleFormatter());
        LOG.addHandler(fileHandler);
        LOG.addHandler(console);
        LOG.setUseParentHandlers(false);
        LOG.setLevel(Level.ALL);
    }

    private static final class Session {
        private Socket socket;
        private PrintWriter out;
        private Thread reader;
        private final AtomicBoolean open = new AtomicBoolean();
        private final AtomicBoolean closing = new AtomicBoolean();

        private void connect(String command) throws IOException {
            if (open.get()) {
                System.out.println("Соединение уже открыто.");
                return;
            }
            String[] parts = command.trim().split("\\s+");
            if (parts.length != 3) {
                System.out.println("Формат: connect <адрес> <порт>");
                return;
            }
            int port;
            try {
                port = Integer.parseInt(parts[2]);
            } catch (NumberFormatException exception) {
                System.out.println("Некорректный порт.");
                return;
            }
            InetAddress address = InetAddress.getByName(parts[1]);
            socket = new Socket(address, port);
            open.set(true);
            LOG.info("Начало соединения " + parts[1] + ":" + port);
            out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(
                    socket.getOutputStream(), StandardCharsets.UTF_8)), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(
                    socket.getInputStream(), StandardCharsets.UTF_8));
            reader = new Thread(() -> readServer(in), "server-reader");
            reader.setDaemon(true);
            reader.start();
        }

        private void readServer(BufferedReader in) {
            try {
                String response;
                while ((response = in.readLine()) != null) {
                    System.out.println("<< " + response);
                    LOG.info("Принято: " + response);
                }
            } catch (IOException exception) {
                if (open.get() && !closing.get()) {
                    LOG.log(Level.WARNING, "Соединение прервано", exception);
                }
            } finally {
                if (open.compareAndSet(true, false)) {
                    LOG.info("Окончание соединения");
                }
            }
        }

        private void send(String line) {
            if (!open.get() || out == null) {
                System.out.println("Нет соединения. Сначала: connect <адрес> <порт>");
                return;
            }
            out.println(line);
            if (out.checkError()) {
                System.out.println("Сервер закрыл соединение.");
            }
        }

        private void close() throws IOException, InterruptedException {
            closing.set(true);
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
            if (reader != null) {
                reader.join(1000);
            }
            if (open.compareAndSet(true, false)) {
                LOG.info("Окончание соединения");
            }
        }
    }
}
