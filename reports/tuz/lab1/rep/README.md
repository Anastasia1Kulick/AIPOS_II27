Министерство образования Республики Беларусь
Учреждение образования
«Брестский государственный технический университет»
Кафедра ИИТ

Лабораторная работа №1
по дисциплине «Аппаратное и программное обеспечение сетей»
Тема: Организация TCP-сервера и TCP-клиента
Вариант 11

Выполнил:
студент 3 курса
группы ИИ-27
Туз Г.С.
Проверила:
Кулик А.Д.

Брест 2026

# Цель

Научиться поднимать TCP-соединение средствами java.net, принять поток символов от клиента и записать его в файл по команде сервера. Для клиента отдельно отработать ввод строки по клавише PgUp, подключение командой connect и журнал только тех событий, которые заданы вариантом.

# Задание

Изучить классы пакета java.net. Сделать TCP-сервер по заданию 2 таблицы 2 и TCP-клиент по варианту 2 таблицы 3. Сервер проверить telnet и собственным клиентом. В отчёт включить титульный лист и листинг с пояснением основных фрагментов.

# Задание 2 сервера

Если в потоке приходит команда `save <имя файла>`, сервер создаёт в своём каталоге текстовый файл с этим именем. В файл попадает всё, что клиент передал после команды, до первого вхождения `#end##`. Маркер в файл не записывается. После записи сервер сообщает клиенту, что файл создан, и размер. Если имя недопустимо или файл записать нельзя, сервер отправляет сообщение об ошибке и закрывает соединение.

# Задание 2 клиента

Строка, набранная на клавиатуре, уходит на сервер по клавише PgUp. В файле протокола фиксируются только:

1. время начала и время окончания соединения;
3. строка, принятая от сервера, и время её приёма.

Переданную на сервер строку протокол не содержит. Команды `disconnect <адрес> <порт>` нет. Адрес и порт задаются командой `connect <адрес> <порт>`, автоматического подключения при запуске нет.

# Структура

```
reports/tuz/lab1/
├── rep/
│   └── README.md
└── src/
    ├── FileServer.java
    ├── FileClient.java
    └── RawConsole.java
```

Рядом с запущенной программой появляются `server.log`, `client.log` и файлы, созданные командой save. В репозиторий их класть не нужно.

# Запуск

Из каталога `src`, два окна:

```
javac -encoding UTF-8 FileServer.java FileClient.java RawConsole.java
java --enable-native-access=ALL-UNNAMED FileServer
java --enable-native-access=ALL-UNNAMED FileClient
```

Флаг нужен, чтобы на Windows перевести консоль в режим, в котором видна клавиша PgUp. В окне клиента:

```
connect 127.0.0.1 8080
save note.txt
первая строка
#end##
exit
```

Каждую строку отправляет PgUp. Enter в raw-режиме только переводит строку внутри набираемого текста. Если консоль raw-режим не дала, программа пишет об этом и отправляет строку по Enter. Команда `disconnect` на сервер не уходит.

Сервер без клиента проверяется так:

```
telnet 127.0.0.1 8080
```

После приветствия `Hello, Student!` те же команды `save`, текст и `#end##`.

# Результаты проверки

Сервер на `127.0.0.1:18083`.

| Что отправлено | Что получил клиент | Файл |
| --- | --- | --- |
| `ping` | `Echo: ping` | не создаётся |
| `save note.txt`, затем `hello`, `world`, `#end##` | `Файл создан: note.txt, размер 12` | `hello` и `world` с переводами строк |
| `save ../secret.txt` | `Ошибка: файл "../secret.txt" нельзя создать`, соединение закрыто | не создаётся |
| `save mid.txt`, затем `abc#end##TAIL` | сообщение о создании и `Echo: TAIL` | в файле только `abc` |
| клиент: `connect`, `save from-client.txt`, `строка один`, `#end##` | `Hello, Student!` и `Файл создан: from-client.txt, размер 22` | текст `строка один` |

Фрагмент `client.log` после этого сеанса:

```
[2026-09-28 12:45:00] Начало соединения 127.0.0.1:18083
[2026-09-28 12:45:00] Принято: Hello, Student!
[2026-09-28 12:45:01] Принято: Файл создан: from-client.txt, размер 22
[2026-09-28 12:45:01] Окончание соединения
```

Строк `save` и `строка один` в журнале нет: это отправленный текст, вариант 2 его не протоколирует. Последовательность PgUp (`ESC [ 5 ~`) распознаётся, последовательность PgDn (`ESC [ 6 ~`) строку не отправляет.

# Как устроена программа

Сервер слушает порт 8080. Сеансы идут по очереди: следующий `accept` выполняется после закрытия текущего сокета. Чужие строки возвращаются как `Echo: ...`. Команда `save` включает накопление тела. Имя с `/`, `\`, `:` или `..` отклоняется сразу, с разрывом соединения. Встреча `#end##` записывает накопленный текст в кодировке UTF-8 и оставляет сеанс открытым. Служебные байты Telnet отбрасываются, чтобы к серверу можно было зайти и через telnet.

Клиент сам сокет при старте не открывает. Команда `connect` создаёт `Socket`, в `client.log` пишется начало соединения, а отдельный поток читает ответы сервера и тут же пишет их в тот же журнал. `exit` закрывает сокет и фиксирует окончание. `RawConsole` на Windows снимает построчный режим консоли и ждёт ESC-последовательность PgUp.

# Код программы

## Сервер

```java
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
```


## Клиент

```java
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
```


## Клавиша PgUp

```java
import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;

/**
 * Чтение строки с клавиатуры. В консоли строка возвращается по PgUp.
 * Если raw-режим недоступен, строка возвращается по Enter.
 */
public final class RawConsole implements Closeable {

    private static final int STD_INPUT_HANDLE = -10;
    private static final int ENABLE_LINE_INPUT = 0x0002;
    private static final int ENABLE_ECHO_INPUT = 0x0004;
    private static final int ENABLE_VIRTUAL_TERMINAL_INPUT = 0x0200;

    private final InputStream in = System.in;
    private final OutputStream out = System.out;
    private final boolean lineMode;
    private final BufferedReader lines;
    private Runnable restore = () -> { };

    public RawConsole() {
        boolean raw = enableRaw();
        lineMode = !raw;
        lines = lineMode ? new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)) : null;
        if (lineMode) {
            System.out.println("Raw-консоль недоступна, строка отправляется по Enter.");
        }
    }

    public boolean lineMode() {
        return lineMode;
    }

    public String readLine() throws IOException {
        if (lineMode) {
            return lines.readLine();
        }
        StringBuilder buffer = new StringBuilder();
        boolean carriageReturn = false;
        while (true) {
            int value = in.read();
            if (value < 0) {
                return buffer.isEmpty() ? null : buffer.toString();
            }
            // PgUp приходит как ESC [ 5 ~. PgDn (ESC [ 6 ~) строку не отправляет.
            if (value == 0x1B) {
                if (readPageUp()) {
                    out.write('\r');
                    out.write('\n');
                    out.flush();
                    return buffer.toString();
                }
                carriageReturn = false;
                continue;
            }
            if (value == '\n' && carriageReturn) {
                carriageReturn = false;
                continue;
            }
            carriageReturn = value == '\r';
            if (value == '\r' || value == '\n') {
                buffer.append('\n');
                out.write('\r');
                out.write('\n');
                out.flush();
                continue;
            }
            if (value == 8 || value == 127) {
                eraseLast(buffer);
                continue;
            }
            if (value >= 0x20) {
                buffer.append(readCharacter(value));
            }
        }
    }

    static boolean pageUpSequence(String parameters, int command) {
        return command == '~' && (parameters.equals("5") || parameters.startsWith("5;"));
    }

    private boolean readPageUp() throws IOException {
        if (in.read() != '[') {
            return false;
        }
        StringBuilder parameters = new StringBuilder();
        int current;
        while ((current = in.read()) >= 0) {
            if (current >= 0x40 && current <= 0x7E) {
                return pageUpSequence(parameters.toString(), current);
            }
            parameters.append((char) current);
            if (parameters.length() > 16) {
                return false;
            }
        }
        return false;
    }

    private String readCharacter(int first) throws IOException {
        int length = first < 0x80 ? 1 : first < 0xE0 ? 2 : first < 0xF0 ? 3 : 4;
        byte[] bytes = new byte[length];
        bytes[0] = (byte) first;
        for (int index = 1; index < length; index++) {
            int next = in.read();
            if (next < 0) {
                break;
            }
            bytes[index] = (byte) next;
        }
        out.write(bytes);
        out.flush();
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private void eraseLast(StringBuilder buffer) throws IOException {
        if (buffer.isEmpty() || buffer.charAt(buffer.length() - 1) == '\n') {
            return;
        }
        buffer.setLength(buffer.length() - 1);
        out.write('\b');
        out.write(' ');
        out.write('\b');
        out.flush();
    }

    private boolean enableRaw() {
        String os = System.getProperty("os.name").toLowerCase();
        try {
            if (os.contains("win")) {
                return enableWindowsRaw();
            }
            if (os.contains("nux") || os.contains("mac") || os.contains("nix")) {
                return enableUnixRaw();
            }
        } catch (Throwable exception) {
            System.err.println("Raw-режим не включён: " + exception.getMessage());
        }
        return false;
    }

    private boolean enableWindowsRaw() throws Throwable {
        Linker linker = Linker.nativeLinker();
        SymbolLookup kernel = SymbolLookup.libraryLookup("Kernel32", Arena.global());
        MethodHandle getStdHandle = linker.downcallHandle(
                kernel.find("GetStdHandle").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        MethodHandle getConsoleMode = linker.downcallHandle(
                kernel.find("GetConsoleMode").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        MethodHandle setConsoleMode = linker.downcallHandle(
                kernel.find("SetConsoleMode").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        MemorySegment handle = (MemorySegment) getStdHandle.invokeExact(STD_INPUT_HANDLE);
        Arena arena = Arena.ofConfined();
        MemorySegment modeSlot = arena.allocate(ValueLayout.JAVA_INT);
        int read = (int) getConsoleMode.invokeExact(handle, modeSlot);
        if (read == 0) {
            arena.close();
            return false;
        }
        int saved = modeSlot.get(ValueLayout.JAVA_INT, 0);
        int raw = (saved & ~(ENABLE_LINE_INPUT | ENABLE_ECHO_INPUT)) | ENABLE_VIRTUAL_TERMINAL_INPUT;
        int written = (int) setConsoleMode.invokeExact(handle, raw);
        arena.close();
        if (written == 0) {
            return false;
        }
        restore = () -> {
            try {
                setConsoleMode.invokeExact(handle, saved);
            } catch (Throwable exception) {
                System.err.println("Не удалось вернуть режим консоли: " + exception.getMessage());
            }
        };
        return true;
    }

    private boolean enableUnixRaw() throws IOException, InterruptedException {
        String saved = shell("stty -g").trim();
        shell("stty raw -echo");
        restore = () -> {
            try {
                shell("stty " + saved);
            } catch (Exception exception) {
                System.err.println("Не удалось вернуть терминал: " + exception.getMessage());
            }
        };
        return true;
    }

    private static String shell(String command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder("/bin/sh", "-c", command)
                .redirectInput(ProcessBuilder.Redirect.INHERIT)
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        process.waitFor();
        return output;
    }

    @Override
    public void close() {
        restore.run();
    }
}
```


# Вывод

Сервер по заданию 2 создаёт файл из потока после `save` и обрезает его по `#end##`. Ошибочное имя разрывает соединение, успешная запись — нет. Клиент по заданию 2 подключается только командой `connect`, отправляет набранное по PgUp и ведёт `client.log` с временем начала, окончания и каждой принятой строкой.
