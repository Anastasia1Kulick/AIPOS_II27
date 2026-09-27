import java.io.*;
import java.net.*;
import java.util.logging.*;

/**
 * TCP-эхо-сервер с поддержкой команды "load <имя_файла>".
 * Функциональность согласно варианту задания:
 *
 * Ведение файла протокола событий реализовано стандартным механизмом java.util.logging
*/

public class FileServer {
    // Порт, на котором сервер принимает соединения
    public static final int PORT = 8080;
    // текущий рабочий каталог процесса сервера
    public static final String FILE_DIR = ".";
    // Имя файла протокола событий
    public static final String LOG_FILE = "server.log";

    private static final Logger logger = Logger.getLogger(FileServer.class.getName());

    static {
        try {
            // root-логгер, убираем у него стандартные обработчики.
            Logger root = Logger.getLogger("");
            Handler[] handlers = root.getHandlers();
            for (Handler h: handlers) root.removeHandler(h);
            // Обработчик для записи в файл
            // Второй аргумент true - не стирать файл
            // а дописывать в конец при каждом запуске сервера
            FileHandler fileHandler = new FileHandler("server.log", true);
            // формат записи
            fileHandler.setFormatter(new SimpleFormatter() {
                private final java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("yyyy-MM-dd HH-mm-ss");

                @Override 
                public synchronized String format(LogRecord r) {
                    return "[" + fmt.format(new java.util.Date(r.getMillis())) + "]" + r.getLevel() + ": " + r.getMessage() + System.lineSeparator();
                }
            });
            logger.addHandler(fileHandler);
            // Обработчик для записи в консоль
            ConsoleHandler console = new ConsoleHandler();
            console.setFormatter(new SimpleFormatter());
            logger.addHandler(console);
            // Без этой строки каждая запись напечатается дважды
            logger.setUseParentHandlers(false);
            // Пропускать сообщения всех уровней важности
            logger.setLevel(Level.ALL);
        } catch (IOException e) {
            System.err.println("Can't set logger: " + e.getMessage());
        }
    }

    public static void main(String[] args) throws IOException {
        // Создание серверного сокета.
        ServerSocket serverSocket = new ServerSocket(PORT);
        logger.info("Server launched on port: " + PORT);
       
        try {
            // Бесконечный цикл обработки соединений.
            // Каждое принятое соединение обрабатывается последовательно
            // сервер не может обслуживать двух клиентов одновременно,
            // пока не завершится сеанс текущего
            while (true) {
                Socket socket = serverSocket.accept();
                logger.info("Start connection: " + socket);

                try {
                    // Создание потоков ввода/вывода поверх сокета
                    BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    PrintWriter out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())), true);

                    String str;
                    // Цикл чтения строк от клиента
                    while ((str = in.readLine()) != null) {
                        logger.info("Recieved from client: \"" + str + "\"");
                        // Ожидается строка вида load <имяфайла>
                        if (str.startsWith("load")) {
                            // Извлекаем имя файла
                            String fileName = str.substring(5).trim();
                            logger.info("File requested: " + fileName);
                            // Передаём файл клиенту
                            sendFile(fileName, out);
                            break;
                        } else {
                            out.println("Echo: " + str);
                            logger.info("Sent to client: \"" + str + "\"");
                        }
                    }
                } catch (IOException e) {
                    logger.log(Level.WARNING, "Exchange error", e);
                } finally {
                    // Гарантированное закрытие сокета
                    logger.info("End session: " + socket);
                    socket.close();
                }
            }
        } finally {
            // Закрытие серверного сокета при выходе
            serverSocket.close();
        }
    }

    private static void sendFile(String fileName, PrintWriter out) {
        File file = new File(FILE_DIR, fileName);
        // Файл не найден или это не файл
        if (!file.exists() || !file.isFile()) {
            out.println("Error: File \"" + fileName + "\" doesn't exist");
            logger.warning("File can't be found" + fileName);
            return;
        }
        try (BufferedReader fileReader = new BufferedReader(new FileReader(file))) {
            // чтобы клиент мог отличить содержимоe файла от возможных ответов сервера
            out.println("File begun: " + fileName);
            String line;
            while ((line = fileReader.readLine()) != null) {
                out.println(line);
            }
            out.println("File end: " + fileName);
            logger.info("File sent succesfull: " + fileName);
        } catch (IOException e) {
            // Ошибка чтения файла
            out.println("ERROR: File cant't be read: \"" + fileName + "\"");
            logger.log(Level.SEVERE, "File read error" + fileName, e);
        }
    }
}