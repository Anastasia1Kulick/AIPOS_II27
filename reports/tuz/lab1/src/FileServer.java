import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 * TCP-сервер задания 2: по команде {@code save <имя файла>} записывает
 * последующий поток символов в файл до маркера {@code #end##}.
 */
public final class FileServer {

    public static final int PORT = 8080;
    private static final String MARKER = "#end##";
    private static final Logger LOG = Logger.getLogger(FileServer.class.getName());

    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : PORT;
        attachLog("server.log");
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            LOG.info("Сервер запущен, порт " + port);
            while (true) {
                Socket socket = serverSocket.accept();
                serve(socket);
            }
        }
    }

    private static void serve(Socket socket) {
        LOG.info("Начало соединения " + socket.getRemoteSocketAddress());
        try (socket) {
            BufferedReader in = new BufferedReader(new InputStreamReader(
                    new TelnetFilter(socket.getInputStream()), StandardCharsets.UTF_8));
            PrintWriter out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(
                    socket.getOutputStream(), StandardCharsets.UTF_8)), true);
            out.println("Hello, Student!");

            // null — обычный обмен. Иначе в body копится текст файла до #end##.
            String fileName = null;
            StringBuilder body = null;
            String line;
            while ((line = in.readLine()) != null) {
                if (body == null) {
                    fileName = saveName(line);
                    if (fileName == null) {
                        out.println("Echo: " + line);
                        continue;
                    }
                    if (target(fileName) == null) {
                        out.println("Ошибка: файл \"" + fileName + "\" нельзя создать");
                        LOG.warning("Отказ в создании файла: " + fileName);
                        break;
                    }
                    body = new StringBuilder();
                    LOG.info("Приём файла " + fileName);
                    continue;
                }
                int marker = line.indexOf(MARKER);
                if (marker < 0) {
                    body.append(line).append('\n');
                    continue;
                }
                body.append(line, 0, marker);
                if (!writeFile(fileName, body.toString(), out)) {
                    break;
                }
                body = null;
                String rest = line.substring(marker + MARKER.length()).trim();
                if (!rest.isEmpty()) {
                    out.println("Echo: " + rest);
                }
            }
        } catch (java.net.SocketException exception) {
            LOG.info("Клиент отключился");
        } catch (IOException exception) {
            LOG.log(Level.WARNING, "Ошибка обмена", exception);
        }
        LOG.info("Окончание соединения");
    }

    private static String saveName(String line) {
        if (!line.startsWith("save ")) {
            return null;
        }
        return line.substring(5).trim();
    }

    private static Path target(String fileName) {
        if (fileName.isEmpty() || fileName.contains("..") || fileName.indexOf('/') >= 0
                || fileName.indexOf('\\') >= 0 || fileName.indexOf(':') >= 0) {
            return null;
        }
        Path path = Path.of(fileName);
        return path.getNameCount() == 1 ? path : null;
    }

    private static boolean writeFile(String fileName, String content, PrintWriter out) {
        Path path = target(fileName);
        if (path == null) {
            out.println("Ошибка: файл \"" + fileName + "\" нельзя создать");
            return false;
        }
        try {
            Files.writeString(path, content, StandardCharsets.UTF_8);
            int size = content.getBytes(StandardCharsets.UTF_8).length;
            out.println("Файл создан: " + fileName + ", размер " + size);
            LOG.info("Создан файл " + fileName + ", размер " + size);
            return true;
        } catch (IOException exception) {
            out.println("Ошибка: файл \"" + fileName + "\" нельзя создать");
            LOG.log(Level.SEVERE, "Не записан файл " + fileName, exception);
            return false;
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

    private static final class TelnetFilter extends FilterInputStream {
        private static final int IAC = 255;
        private static final int SB = 250;
        private static final int SE = 240;

        private TelnetFilter(InputStream in) {
            super(in);
        }

        @Override
        public int read() throws IOException {
            while (true) {
                int current = in.read();
                if (current < 0 || current != IAC) {
                    return current;
                }
                int command = in.read();
                if (command < 0 || command == IAC) {
                    return command < 0 ? -1 : IAC;
                }
                if (command == SB) {
                    skipSubnegotiation();
                    continue;
                }
                if (command >= 251 && command <= 254 && in.read() < 0) {
                    return -1;
                }
            }
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            if (length == 0) {
                return 0;
            }
            int first = read();
            if (first < 0) {
                return -1;
            }
            buffer[offset] = (byte) first;
            int count = 1;
            while (count < length && in.available() > 0) {
                int next = read();
                if (next < 0) {
                    break;
                }
                buffer[offset + count++] = (byte) next;
            }
            return count;
        }

        private void skipSubnegotiation() throws IOException {
            int previous = 0;
            int current;
            while ((current = in.read()) >= 0) {
                if (previous == IAC && current == SE) {
                    return;
                }
                previous = current;
            }
        }
    }
}
