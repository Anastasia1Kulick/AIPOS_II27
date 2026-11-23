Министерство образования Республики Беларусь
Учреждение образования
«Брестский государственный технический университет»
Кафедра ИИТ

Лабораторная работа №2
по дисциплине «Аппаратное и программное обеспечение сетей»
Тема: Организация UDP-сервера и UDP-клиента
Вариант 11

Выполнил:
студент 3 курса
группы ИИ-27
Туз Г.С.
Проверила:
Кулик А.Д.

Брест 2026

# Цель

Повторить функции варианта 11 на протоколе UDP: сервер по-прежнему сохраняет текст в файл, клиент по-прежнему подключается командой и отправляет строку по PgUp. Обмен идёт датаграммами, без установки TCP-соединения.

# Задание

Вариант 11 задаёт те же функции, что и в первой работе.

Сервер, задание 2. По команде `save <имя файла>` сервер создаёт текстовый файл в своём каталоге. В файл входит поток после команды до первого `#end##`. Маркер в файл не пишется. После записи сервер сообщает имя и размер. Если файл создать нельзя, сервер отвечает ошибкой и этот обмен прекращает.

Клиент, задание 2. Строка уходит по клавише PgUp. В `client.log` записываются только время начала и окончания и каждая строка, принятая от сервера, вместе со временем приёма. Отправленный текст в журнал не попадает. Команды `disconnect` нет. Адрес задаётся командой `connect <адрес> <порт>`.

Проверка по методичке: адрес `127.0.0.1`, порт `666`.

# Запуск

Из каталога `src`, два окна:

```
javac -encoding UTF-8 UdpServer.java UdpClient.java RawConsole.java
java UdpServer
java --enable-native-access=ALL-UNNAMED UdpClient
```

В клиенте каждая строка отправляется клавишей PgUp:

```
connect 127.0.0.1 666
save note.txt
первая строка
#end##
exit
```

Сервер останавливается сочетанием Ctrl+C. Файл появляется в том каталоге, откуда запущен сервер.

# Результаты проверки

| Датаграмма | Ответ | Файл |
| --- | --- | --- |
| `ping` | `Echo: ping` | не создаётся |
| `save note.txt`, затем `hello`, затем `#end##` | `Приём файла note.txt`, затем `Файл создан: note.txt, размер 6` | `hello` и перевод строки |
| `save ../secret.txt` | `Ошибка: файл "../secret.txt" нельзя создать` | не создаётся |
| `save mid.txt`, затем `abc#end##` | сообщение о создании, размер 3 | в файле только `abc` |
| клиент: `строка один` между `save` и `#end##` | `Файл создан: from-client.txt, размер 22` | текст `строка один` |

Фрагмент `client.log`:

```
[2026-11-09 10:38:50] Начало соединения 127.0.0.1:666
[2026-11-09 10:38:51] Принято: Файл создан: from-client.txt, размер 22
[2026-11-09 10:38:52] Окончание соединения
```

Строк `save` и `строка один` в журнале нет.

# Листинг UdpServer.java

```java
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class UdpServer {

    public static final int PORT = 666;
    private static final String MARKER = "#end##";

    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : PORT;
        try (DatagramSocket socket = new DatagramSocket(port)) {
            System.out.println("UDP-сервер запущен, порт " + port);
            byte[] buffer = new byte[4096];
            String fileName = null;
            StringBuilder body = null;
            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                String line = text(packet).trim();
                if (line.isEmpty()) {
                    continue;
                }
                if (body == null) {
                    if (line.startsWith("save ")) {
                        fileName = line.substring(5).trim();
                        if (target(fileName) == null) {
                            reply(socket, packet, "Ошибка: файл \"" + fileName + "\" нельзя создать");
                            continue;
                        }
                        body = new StringBuilder();
                        reply(socket, packet, "Приём файла " + fileName);
                    } else {
                        reply(socket, packet, "Echo: " + line);
                    }
                    continue;
                }
                int marker = line.indexOf(MARKER);
                if (marker < 0) {
                    body.append(line).append('\n');
                    continue;
                }
                body.append(line, 0, marker);
                reply(socket, packet, store(fileName, body.toString()));
                body = null;
            }
        }
    }

    private static String store(String fileName, String content) {
        Path path = target(fileName);
        if (path == null) {
            return "Ошибка: файл \"" + fileName + "\" нельзя создать";
        }
        try {
            Files.writeString(path, content, StandardCharsets.UTF_8);
            int size = content.getBytes(StandardCharsets.UTF_8).length;
            System.out.println("Создан файл " + fileName + ", размер " + size);
            return "Файл создан: " + fileName + ", размер " + size;
        } catch (IOException exception) {
            System.out.println("Не записан файл " + fileName);
            return "Ошибка: файл \"" + fileName + "\" нельзя создать";
        }
    }

    private static Path target(String fileName) {
        if (fileName == null || fileName.isEmpty() || fileName.contains("..")
                || fileName.indexOf('/') >= 0 || fileName.indexOf('\\') >= 0 || fileName.indexOf(':') >= 0) {
            return null;
        }
        Path path = Path.of(fileName);
        return path.getNameCount() == 1 ? path : null;
    }

    private static void reply(DatagramSocket socket, DatagramPacket request, String message) throws IOException {
        byte[] data = message.getBytes(StandardCharsets.UTF_8);
        DatagramPacket response = new DatagramPacket(
                data, data.length, request.getAddress(), request.getPort());
        socket.send(response);
        System.out.println("Ответ: " + message);
    }

    private static String text(DatagramPacket packet) {
        return new String(packet.getData(), packet.getOffset(), packet.getLength(), StandardCharsets.UTF_8);
    }
}
```

# Листинг UdpClient.java

```java
import java.io.IOException;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

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
```

Класс `RawConsole` читает клавиатуру и возвращает строку по PgUp. Это тот же приём, что в лабораторной работе №1.

# Вывод

UDP-сервер варианта 11 принимает датаграммы на порту 666 и по команде `save` записывает текст до `#end##`. Клиент задаёт адрес командой `connect`, отправляет строку по PgUp и ведёт журнал только начала, конца и принятых ответов.
