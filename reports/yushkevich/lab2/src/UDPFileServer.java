import java.io.*;
import java.net.*;
import java.util.logging.*;

/**
 * UDP-эхо-сервер с поддержкой команды "load <имя_файла>".
 *
 * Функциональность согласно варианту задания:
 *   - приём датаграммы с командой "load fname.txt";
 *   - отсылка клиенту содержимого файла fname.txt из каталога сервера;
 *   - если файла нет — сообщение об ошибке и разрыв сеанса;
 *   - ведение файла протокола событий (java.util.logging):
 *        время начала/окончания сеанса, принятые и отправленные строки.
 */
public class UDPFileServer {

    // Порт, на котором сервер принимает датаграммы (по заданию — 666)
    public static final int PORT = 6666;
    // Рабочий каталог процесса сервера
    public static final String FILE_DIR = ".";
    // Имя файла протокола событий
    public static final String LOG_FILE = "server.log";
    // Максимальный размер полезной нагрузки одной датаграммы
    public static final int BUF_SIZE = 1024;
    // Кодировка обмена
    public static final String CHARSET = "UTF-8";

    private static final Logger logger = Logger.getLogger(UDPFileServer.class.getName());

    static {
        try {
            // Убираем стандартные обработчики root-логгера
            Logger root = Logger.getLogger("");
            for (Handler h : root.getHandlers()) root.removeHandler(h);

            // Обработчик записи в файл; true — дописывать, а не стирать
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

            // Чтобы каждая запись не печаталась дважды
            logger.setUseParentHandlers(false);
            // Пропускать сообщения всех уровней
            logger.setLevel(Level.ALL);

        } catch (IOException e) {
            System.err.println("Can't set logger: " + e.getMessage());
        }
    }

    public static void main(String[] args) throws IOException {
        // Создание сокета UDP. Привязка к порту PORT.
        DatagramSocket socket = new DatagramSocket(PORT);
        logger.info("UDP server launched on port: " + PORT);

        try {
            // Бесконечный цикл обслуживания датаграмм.
            // Каждый запрос обрабатывается последовательно (как и в TCP-версии)
            while (true) {
                // Буфер под полезную нагрузку датаграммы
                byte[] buf = new byte[BUF_SIZE];
                DatagramPacket packet = new DatagramPacket(buf, buf.length);

                // receive() блокируется, пока не придёт датаграмма
                socket.receive(packet);

                InetAddress clientAddress = packet.getAddress();
                int clientPort = packet.getPort();

                // Извлекаем только реально принятые байты (getLength()),
                // иначе в строку попадут "хвосты" нулей из буфера
                String received = new String(packet.getData(), 0, packet.getLength(), CHARSET).trim();

                logger.info("Start session with client: " + clientAddress + ":" + clientPort);
                logger.info("Received from client: \"" + received + "\"");

                if (received.startsWith("load")) {
                    // Ожидается строка вида "load <имяфайла>"
                    String fileName = received.substring(4).trim();
                    logger.info("File requested: " + fileName);
                    // Передаём файл клиенту (или сообщение об ошибке)
                    sendFile(fileName, socket, clientAddress, clientPort);
                } else {
                    // Обычное эхо
                    sendLine("Echo: " + received, socket, clientAddress, clientPort);
                }

                logger.info("End session with client: " + clientAddress + ":" + clientPort);
            }
        } finally {
            // Закрытие сокета при выходе
            socket.close();
        }
    }

    /**
     * Отправляет файл клиенту построчно отдельными датаграммами.
     * Возвращает false, если файл не найден (сеанс считается разорванным).
     */
    private static boolean sendFile(String fileName, DatagramSocket socket,
                                    InetAddress address, int port) {
        File file = new File(FILE_DIR, fileName);

        // Файл не существует или это не файл — ошибка и разрыв сеанса
        if (!file.exists() || !file.isFile()) {
            sendLine("Error: File \"" + fileName + "\" doesn't exist", socket, address, port);
            logger.warning("File can't be found: " + fileName);
            return false;
        }

        try (BufferedReader fileReader = new BufferedReader(new FileReader(file))) {
            // Чтобы клиент мог отличить содержимое файла от прочих ответов
            sendLine("File begun: " + fileName, socket, address, port);

            String line;
            while ((line = fileReader.readLine()) != null) {
                sendLine(line, socket, address, port);
            }

            sendLine("File end: " + fileName, socket, address, port);
            logger.info("File sent successfully: " + fileName);
            return true;

        } catch (IOException e) {
            // Ошибка чтения файла
            sendLine("ERROR: File can't be read: \"" + fileName + "\"", socket, address, port);
            logger.log(Level.SEVERE, "File read error: " + fileName, e);
            return false;
        }
    }

    /**
     * Отправляет одну строку одной датаграммой.
     */
    private static void sendLine(String line, DatagramSocket socket,
                                 InetAddress address, int port) {
        try {
            byte[] data = line.getBytes(CHARSET);

            // Датаграмма не может быть произвольно большой — обрезаем слишком длинные строки
            if (data.length > BUF_SIZE) {
                logger.warning("Line is too long and will be truncated (" + data.length + " bytes)");
                data = java.util.Arrays.copyOf(data, BUF_SIZE);
            }

            DatagramPacket packet = new DatagramPacket(data, data.length, address, port);
            socket.send(packet);

            logger.info("Sent to client: \"" + line + "\"");
        } catch (IOException e) {
            logger.log(Level.WARNING, "Send error", e);
        }
    }
}