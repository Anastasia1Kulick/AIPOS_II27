<p align="center">Министерство образования Республики Беларусь</p>
<p align="center">Учреждение образования</p>
<p align="center">“Брестский Государственный технический университет”</p>
<p align="center">Кафедра ИИТ</p>
<br><br><br><br><br><br><br>
<p align="center">Лабораторная работа №1</p>
<p align="center">По дисциплине “Аппаратное и Программное обеспечение сетей”</p>
<p align="center">Тема: “Организация TCP – сервера/клиента”</p>
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
    ├── FileServer.java
    ├── RawConsole.java
    ├── FileClient.java
    ├── test.txt
    └── server.log (создаётся автоматически)
```
# Код программы
## Клиентская часть
```java
import java.io.*;
import java.net.*;
import java.util.logging.*;

/*
 * TCP-клиент для взаимодействия с FileServer.
 *
 * Функциональность согласно варианту задания:
 *   1) автоматическое подключение к серверу по адресу по умолчанию
 *      при запуске клиента;
 *   2) отсылка введённой строки серверу по нажатию клавиши PgDn
 *      (реализовано во вспомогательном классе RawConsole);
 *   3) ведение файла протокола событий server.log:
 *        - время начала и окончания соединения;
 *        - передаваемую серверу строку и время передачи строки;
 *        - принимаемую от сервера строку и время приема строки
 */

public class FileClient {
    // Адрес сервера по умолчанию
    public static final String DEFAULT_HOST = "127.0.0.1";
    // Порт по умолчанию
    public static final int DEFAULT_PORT = FileServer.PORT;
    // Логгер класса. Записи попадают в server.log и в консоль
    private static final Logger logger = Logger.getLogger(FileClient.class.getName());

        public static void main (String[] args) throws IOException{
        // Oпределение адреса и порта сервера
        // Если пользователь передал аргументы командной строки используем их
        // иначе берём значения по умолчанию 
        String host = args.length > 0 ? args[0] : DEFAULT_HOST;
        int port = args.length > 1 ? Integer.parseInt(args[1]) : DEFAULT_PORT;
        // Установка соединения с сервером
        InetAddress addr = InetAddress.getByName(host);
        logger.info("Connect to " + addr + ":" + port);

        Socket socket = new Socket(addr, port);
        logger.info("Connected: " + socket);

        try (RawConsole console = new RawConsole()){
            // Создание потоков ввода/вывода поверх сокета
            // Байтовый InputStream сокета оборачивается в символьный Reader
            // через InputStreamReader, а затем в BufferedReader для
            // построчного чтения
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())), true);

            // Поток-читатель ответов сервера
            Thread readerThread = new Thread(() -> {
                try {
                    String response;
                    // readLine() возвращает null, когда сервер закрыл соединение
                    while ((response = in.readLine()) != null) {
                        // \r\n — обязательно иначе терминал не будет возвращать каретку
                        System.out.print("[SERVER]" + response + "\r\n");
                    }
                } catch (IOException e) {
                    logger.log(Level.WARNING, "Disconnected", e);
                }
            });
            readerThread.start();

            System.out.print("Enter command (example: load fname.txt)\r\n");
            System.out.print("Enter 'exit' to exit\r\n");
            // console.readLine() работает в raw-режиме и возвращает строку по нажатию PgDn
            String line;
            while ((line = console.readLine()) != null) {
                // Локальная команда выхода из клиента.
                if ("exit".equalsIgnoreCase(line)) break;
                out.println(line);
            }
        } finally {
            // finally выполняется всегда: и при нормальном выходе,
            // и при исключении. Закрытие сокета освобождает
            // сетевые ресурсы
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
```