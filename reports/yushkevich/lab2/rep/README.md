<p align="center">Министерство образования Республики Беларусь</p>
<p align="center">Учреждение образования</p>
<p align="center">“Брестский Государственный технический университет”</p>
<p align="center">Кафедра ИИТ</p>
<br><br><br><br><br><br><br>
<p align="center">Лабораторная работа №2</p>
<p align="center">По дисциплине “Аппаратное и Программное обеспечение сетей”</p>
<p align="center">Тема: “Организация UDP – сервера/клиента”</p>
<p align="center">Вариант: 9</p>
<br><br><br><br><br>
<p align="right">Выполнил:</p>
<p align="right">Студент 3 курса</p>
<p align="right">Группы ИИ-27</p>
<p align="right">Юшкевич А.Ю.</p>
<p align="right">Проверила:</p>
<p align="right">Кулик А.Д.</p>
<br><br><br><br><br>
<p align="center">Брест 2026</p>

# Цель
Изучить основы программирования сетевых приложений на базе библиотеки java.net;
Приобрести навыки по практическому использованию библиотеки для реализации сетевых приложений на базе протоколов TCP и UDP.

# Задание на выполнение
```Изучить теоретический материал, функции и классы пакета java.net и листинг программыреализации TCP-сервера. Получить индивидуальное задание у преподавателя.```

```Разработать программу работы TCP-эхо-сервера, выполняющую функции согласноварианта задания (см. приложения). В качестве клиента использовать программу telnet.Выполнить проверку программы согласно методике, приведенной в разделе 7.1.Продемонстрировать работу системы преподавателю.```

```Представить отчет, содержащий титульный лист, листинг программы с подробнымикомментариями основных фрагментов программы.8.4 Подготовиться к защите лабораторной работы по теоретическому материалу, функциям,собственным результатам, полученным в ходе выполнения лабораторной работы```

# Задания для реализации TCP сервера
Отсылка клиенту содержимого текстового файла <имя файла> в случае приема сервером в потокесимволов команды loadfname.txt, <имя файла> - имя некоторого текстового файла, находящегосяв каталоге сервера. В случае, если запрашиваемый файл отсутствует в каталоге сервера, сервердолжен отослать сообщение об этом и разорвать соединение

# Задания для реализации TCP клиента
Ввод символов с отсылкой введенной строки по нажатию наклавишу PgDn

Ведение файла протокола событий, включающих*: 
1. время начала и окончания соединения;
2. передаваемую серверу строку и время передачи строки;
3. принимаемую от сервера строку и время приема строки

Автоматическое подключение к серверу с заданным по умолчанию адресом при запускеклиента

# Структура программы
```
reports/yushkevich/lab1/
├── rep/
│   └── README.md
└── src/
    ├── UDPFileServer.java
    ├── RawConsole.java
    ├── UDPFileClient.java
    ├── test.txt
    └── server.log (создаётся автоматически)
```
# Код программы
## Клиентская часть
```java
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
```
## Обработка клавиши введения
```java
import java.io.*;

/**
 * Обёртка над стандартным вводом System.in, работающая в raw-режиме терминала.
 * Зачем нужен этот класс.
 * System.in читает поток байт, а PgDn посылает escape-последовательность ^[[5~.
 * Чтобы её поймать, нужно перевести терминал в raw-режим и парсить байты вручную
 *
 * В raw-режиме:
 *   - каждый нажатый символ сразу передаётся приложению (без ожидания Enter);
 *   - специальные клавиши больше не обрабатываются
 *   - автоматическое эхо отключено, поэтому каждый введённый символ надо
 *     печатать вручную.
 *
 * Именно это и делает класс RawConsole: переводит терминал в raw-режим
 * через утилиту stty, читает байты из System.in, распознаёт PgDn и
 * возвращает накопленную строку. При закрытии восстанавливает исходные
 * настройки терминала.
 */

public class RawConsole implements Closeable {
    // Проверка ОС: stty доступна только на Unix-подобных системах (Linux, macOS, BSD). 
    // На Windows класс работать не будет
    private static final boolean IS_UNIX = System.getProperty("os.name").toLowerCase().matches(".*nux|nix|mac.*");
    // Стандартный ввод - источник байтов от клавиатуры
    private final InputStream in = System.in;
    // Стандартный вывод - используется для ручного эха вводимых символов
    private final OutputStream out = System.out;
    // Буфер текущей набираемой строки.
    // Символы накапливаются здесь до тех пор, пока пользователь не нажмёт PgDn
    private final StringBuilder buffer = new StringBuilder();
    // Сохранённые настройки терминала.
    // Нужны для восстановления исходного состояния при закрытии
    private String savedStty;

    public RawConsole () {
        if (IS_UNIX) {
            try {
                // для восстановления исходного состояния
                savedStty = exec("stty -g").trim();
                // Включаем raw-режим и отключаем автоэхо
                exec("stty raw -echo");
            } catch (IOException e) {
                System.err.println("Can't set raw mode: " + e.getMessage());
            }
        }
    }
    // Запускает shell-команду и возвращает её stdout
    private static String exec (String cmd) throws IOException {
        ProcessBuilder pb = new ProcessBuilder("/bin/sh", "-c", cmd);
        // Redirect.INHERIT передаёт дочернему процессу тот же самый stdin, что и у Java-процесса
        pb.redirectInput(ProcessBuilder.Redirect.INHERIT);
        pb.redirectErrorStream(true);

        Process p = pb.start();
        // Читаем весь stdout команды в строку.
        StringBuilder sb = new StringBuilder();

        // Читаем весь stdout команды в строку
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line;

            while ((line = r.readLine()) != null) sb.append(line).append("\n");
        }
        // Ждём, чтобы к моменту возврата stty гарантированно применила настройки
        try {
            p.waitFor();
        } catch (InterruptedException ignored) {}
        return sb.toString();
    }

    public String readLine() throws IOException {
        buffer.setLength(0);
        int b;
        // in.read() возвращает -1 при EOF
        while ((b = in.read()) != -1) {
            // Обработка escape-последовательности PgDn
            if (b == 0x1b) {
                if (in.read() == '[' && in.read() == '5' && in.read() == '~') {
                    // переводим курсор на новую строку и возвращаем накопленный буфер
                    out.write('\n');
                    out.flush();
                    return buffer.toString();
                }
                continue;
            }
            // Обработка Enter
            if (b == '\r' || b == '\n') {
                
                out.write('\n');
                out.flush();
                return buffer.toString();
            }
            // Добавляем символ в буфер и эхоим его вручную
            if (b >= 0x20) {
                buffer.append((char) b);
                out.write(b);
                out.flush();
            }
        }
        return null;
    }
     @Override 
        public void close() {
            if (IS_UNIX && savedStty != null) {
                try {
                    // возвращает терминал в то состояние, которое было до включения raw-режима
                    exec("stty " + savedStty);
                } catch (IOException e) {
                    System.err.println("Can't restore terminal: " + e.getMessage());
                }
            }
        }
}
```
## Серверная часть
```java
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
```