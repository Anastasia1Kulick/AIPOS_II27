import java.io.IOException;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * UDP-клиент задания 2.
 * Адрес задаёт команда connect. Строка уходит по PgUp.
 * В client.log пишутся начало, конец и принятые ответы.
 */
public class UdpClient {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static void main(String[] args) throws Exception {
        try (RawConsole console = new RawConsole()) {
            System.out.println("Подключение: connect <адрес> <порт>");
            System.out.println(console.lineMode()
                    ? "Отправка строки: Enter. Выход: exit"
                    : "Отправка строки: PgUp. Выход: exit");
            Session session = new Session();
            String line;
            while ((line = console.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
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
                session.send(line);
            }
            session.close();
        }
    }

    private static final class Session {
        private DatagramSocket socket;
        private InetAddress address;
        private int port;
        private PrintWriter log;
        private Thread reader;
        private volatile boolean open;

        private void connect(String command) throws IOException {
            if (open) {
                System.out.println("Адресат уже задан.");
                return;
            }
            String[] parts = command.trim().split("\\s+");
            if (parts.length != 3) {
                System.out.println("Формат: connect <адрес> <порт>");
                return;
            }
            try {
                port = Integer.parseInt(parts[2]);
            } catch (NumberFormatException exception) {
                System.out.println("Некорректный порт.");
                return;
            }
            address = InetAddress.getByName(parts[1]);
            socket = new DatagramSocket();
            log = new PrintWriter("client.log", StandardCharsets.UTF_8);
            open = true;
            write("Начало соединения " + parts[1] + ":" + port);
            reader = new Thread(this::receive, "udp-receiver");
            reader.setDaemon(true);
            reader.start();
        }

        private void receive() {
            byte[] buffer = new byte[4096];
            while (open) {
                try {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);
                    String message = new String(
                            packet.getData(), packet.getOffset(), packet.getLength(), StandardCharsets.UTF_8);
                    System.out.println("<< " + message);
                    write("Принято: " + message);
                } catch (SocketException exception) {
                    break;
                } catch (IOException exception) {
                    if (open) {
                        System.out.println("Ошибка приёма: " + exception.getMessage());
                    }
                    break;
                }
            }
        }

        private void send(String line) throws IOException {
            if (!open) {
                System.out.println("Нет соединения. Сначала: connect <адрес> <порт>");
                return;
            }
            byte[] data = line.getBytes(StandardCharsets.UTF_8);
            socket.send(new DatagramPacket(data, data.length, address, port));
        }

        private void close() throws InterruptedException {
            open = false;
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
            if (reader != null) {
                reader.join(1000);
            }
            if (log != null) {
                write("Окончание соединения");
                log.close();
                log = null;
            }
        }

        private void write(String message) {
            if (log != null) {
                log.println("[" + LocalDateTime.now().format(TIME) + "] " + message);
                log.flush();
            }
        }
    }
}
