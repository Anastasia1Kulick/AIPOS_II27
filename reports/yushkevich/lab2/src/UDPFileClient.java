import java.io.*;
import java.net.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.*;

/**
 * UDP-клиент для взаимодействия с UDPServer.
 *
 * Функциональность согласно варианту задания:
 *   1) автоматическое подключение к серверу по адресу по умолчанию
 *      при запуске клиента;
 *   2) отсылка введённой строки серверу по нажатию клавиши PgDn
 *      (реализовано во вспомогательном классе RawConsole);
 *   3) ведение файла протокола событий:
 *        - время начала и окончания соединения;
 *        - передаваемую серверу строку и время передачи;
 *        - принимаемую от сервера строку и время приёма.
 */
public class UDPFileClient {

    // Адрес сервера по умолчанию
    public static final String DEFAULT_HOST = "127.0.0.1";
    // Порт по умолчанию — тот же, что у сервера
    public static final int DEFAULT_PORT = UDPFileServer.PORT;
    // Файл протокола событий клиента.
    public static final String LOG_FILE = "client.log";

    private static final Logger logger = Logger.getLogger(UDPFileClient.class.getName());

    static {
        try {
            Logger root = Logger.getLogger("");
            for (Handler h : root.getHandlers()) root.removeHandler(h);

            FileHandler fileHandler = new FileHandler(LOG_FILE, true);
            fileHandler.setFormatter(new SimpleFormatter() {
                private final java.text.SimpleDateFormat fmt =
                        new java.text.SimpleDateFormat("yyyy-MM-dd HH-mm-ss");

                @Override
                public synchronized String format(LogRecord r) {
                    return "[" + fmt.format(new java.util.Date(r.getMillis())) + "] "
                            + r.getLevel() + ": " + r.getMessage()
                            + System.lineSeparator();
                }
            });
            logger.addHandler(fileHandler);

            logger.setUseParentHandlers(false);
            logger.setLevel(Level.ALL);

        } catch (IOException e) {
            System.err.println("Can't set logger: " + e.getMessage());
        }
    }

    public static void main(String[] args) throws IOException {
        // Определение адреса и порта сервера:
        // аргументы командной строки имеют приоритет над значениями по умолчанию
        String host = args.length > 0 ? args[0] : DEFAULT_HOST;
        int port = args.length > 1 ? Integer.parseInt(args[1]) : DEFAULT_PORT;

        InetAddress addr = InetAddress.getByName(host);
        logger.info("Connect to " + addr + ":" + port);

        // Создание сокета UDP. Локальный порт выделяется автоматически.
        DatagramSocket socket = new DatagramSocket();
        // Фиксируем единственного партнёра: датаграммы от других адресов
        // приниматься не будут (аналог "соединения" в TCP)
        socket.connect(addr, port);
        // Тайм-аут нужен, чтобы поток-читатель периодически мог проверить
        // флаг завершения сеанса, а не "висеть" в receive() навсегда
        socket.setSoTimeout(500);

        logger.info("Connected: " + socket.getLocalSocketAddress() + " -> " + addr + ":" + port);

        // Признак того, что сервер сообщил об ошибке и разорвал сеанс
        final AtomicBoolean sessionBroken = new AtomicBoolean(false);

        // Поток-читатель ответов сервера
        Thread readerThread = new Thread(() -> {
            byte[] buf = new byte[UDPFileServer.BUF_SIZE];
            while (!sessionBroken.get() && !socket.isClosed()) {
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                try {
                    socket.receive(packet);

                    String response = new String(packet.getData(), 0,
                            packet.getLength(), UDPFileServer.CHARSET);

                    // \r\n — иначе терминал не возвращает каретку
                    System.out.print("\r\n[SERVER] " + response + "\r\n");
                    logger.info("\r\nReceived from server: " + response);

                    // Сервер сообщил об ошибке — сеанс разорван
                    if (response.startsWith("Error")) {
                        logger.info("Server broke the connection");
                        sessionBroken.set(true);
                    }

                } catch (SocketTimeoutException e) {
                    // штатная ситуация: данных пока нет — просто ждём дальше
                } catch (IOException e) {
                    if (!socket.isClosed()) {
                        logger.log(Level.WARNING, "Receive error", e);
                    }
                    break;
                }
            }
        });
        readerThread.setDaemon(true);
        readerThread.start();

        try (RawConsole console = new RawConsole()) {
            System.out.print("\r\nEnter command (example: load fname.txt)\r\n");
            System.out.print("Enter 'exit' to exit\r\n");

            // console.readLine() работает в raw-режиме и возвращает строку по PgDn
            String line;
            while (!sessionBroken.get() && (line = console.readLine()) != null) {
                // Локальная команда выхода из клиента
                if ("exit".equalsIgnoreCase(line)) break;

                byte[] data = line.getBytes(UDPFileServer.CHARSET);
                DatagramPacket packet = new DatagramPacket(data, data.length, addr, port);
                socket.send(packet);

                logger.info("Sent to server: " + line);
            }
        } finally {
            // finally выполняется всегда: и при нормальном выходе, и при исключении
            logger.info("Close connection");
            socket.close();
        }
    }
}