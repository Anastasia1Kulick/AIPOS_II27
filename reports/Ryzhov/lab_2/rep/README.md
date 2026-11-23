<p align="center">Министерство образования Республики Беларусь</p>
<p align="center">Учреждение образования</p>
<p align="center">«Брестский государственный технический университет»</p>
<p align="center">Кафедра ИИТ</p>
<br><br><br><br><br><br><br>
<p align="center">Лабораторная работа №2</p>
<p align="center">По дисциплине «Аппаратное и программное обеспечение сетей»</p>
<p align="center">Тема: «Организация TCP – клиента»</p>
<p align="center">Вариант: 9</p>
<br><br><br><br><br>
<p align="right">Выполнил:</p>
<p align="right">Студент 3 курса</p>
<p align="right">Рыжов И.А.</p>
<p align="right">Проверила:</p>
<p align="right">Кулик А.Д.</p>
<br><br><br><br><br>
<p align="center">Брест 2026</p>

# Цель

Изучить основы программирования сетевых приложений на базе библиотеки java.net. Приобрести навыки по практическому использованию библиотеки для реализации сетевых приложений на базе протокола TCP.

# Задание на выполнение

9.1 Изучить теоретический материал, функции и классы пакета java.net и листинг программ реализации TCP-клиента.

9.2 Разработать программу работы TCP-клиента. Выполнить проверку программы с использованием разработанного в предыдущей лабораторной работе TCP-сервера.

9.3 Представить отчет, содержащий титульный лист, листинг программы с подробными комментариями основных фрагментов программы.

9.4 Подготовиться к защите лабораторной работы.

# Задания для реализации TCP сервера

Вариант 9, задание №1: отсылка клиенту содержимого текстового файла при получении команды `load fname.txt`. Если файл отсутствует в каталоге сервера, сервер должен отправить сообщение об этом и разорвать соединение.

# Задания для реализации TCP клиента

Вариант 9, задание №4:

- Отправка введённой строки по нажатию клавиши PgDn.
- Ведение файла протокола событий: время начала и окончания соединения, передаваемые и принимаемые строки с временем передачи и приёма.
- Автоматическое подключение к серверу с адресом по умолчанию.
- Команда disconnect не предусмотрена.

# Структура программы

```text
lab2/
├── README.md
├── TCPClient.java
├── TCPServer.java
├── server_files/
│   └── fname.txt
└── client.log (создаётся автоматически)
```

# Код программы

## Клиентская часть

```java
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class TCPClient {
    private static final String HOST = "127.0.0.1";
    private static final int PORT = 6666;
    private static final Path LOG_FILE = Paths.get("client.log");
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private Socket socket;
    private PrintWriter writer;
    private final JFrame frame = new JFrame("TCP-клиент — вариант 9");
    private final JTextField input = new JTextField();
    private final JTextArea output = new JTextArea();

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new TCPClient().start());
    }

    private void start() {
        output.setEditable(false);
        frame.add(new JLabel("Введите load fname.txt и нажмите PgDn"), BorderLayout.NORTH);
        frame.add(new JScrollPane(output), BorderLayout.CENTER);
        frame.add(input, BorderLayout.SOUTH);

        // Отправка введённой строки только по клавише Page Down.
        input.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_DOWN, 0), "send");
        input.getActionMap().put("send", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                send();
            }
        });
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                closeConnection();
            }
        });
        frame.setSize(650, 400);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        input.requestFocusInWindow();
        input.setEnabled(false);

        // Автоматическое подключение при запуске программы.
        try {
            socket = new Socket();
            socket.connect(new InetSocketAddress(HOST, PORT), 2000);
            writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
            event("Начало соединения с " + HOST + ":" + PORT);
            input.setEnabled(true);
            input.requestFocusInWindow();
            // Отдельный поток принимает ответы, не блокируя интерфейс.
            new Thread(this::receive, "receiver").start();
        } catch (IOException e) {
            event("Ошибка подключения: " + e.getMessage());
            closeConnection();
        }
    }

    private synchronized void send() {
        String text = input.getText();
        if (text.trim().isEmpty() || writer == null || socket.isClosed()) return;
        writer.println(text);
        if (writer.checkError()) {
            event("Ошибка отправки");
            closeConnection();
            return;
        }
        event("Отправлено: " + text);
        input.setText("");
    }

    private void receive() {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                event("Получено: " + line);
            }
        } catch (IOException e) {
            if (!socket.isClosed()) event("Ошибка приёма: " + e.getMessage());
        } finally {
            closeConnection();
        }
    }

    private synchronized void closeConnection() {
        if (socket != null && !socket.isClosed()) {
            boolean connected = socket.isConnected();
            try {
                socket.close();
            } catch (IOException e) {
                event("Ошибка закрытия: " + e.getMessage());
            }
            if (connected) event("Окончание соединения");
        }
        SwingUtilities.invokeLater(() -> input.setEnabled(false));
    }

    // Записываем время соединения, отправки, приёма и отключения в файл.
    private synchronized void event(String message) {
        String entry = "[" + LocalDateTime.now().format(FORMAT) + "] " + message;
        SwingUtilities.invokeLater(() -> output.append(entry + "\n"));
        try (BufferedWriter log = Files.newBufferedWriter(LOG_FILE, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            log.write(entry);
            log.newLine();
        } catch (IOException e) {
            System.err.println("Ошибка записи журнала: " + e.getMessage());
        }
    }
}
```

## Серверная часть

```java
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

public class TCPServer {
    private static final int PORT = 6666;
    private static final Path DIRECTORY = Paths.get("server_files");

    public static void main(String[] args) throws IOException {
        Files.createDirectories(DIRECTORY);
        // Сервер работает на локальном компьютере и ожидает клиентов.
        try (ServerSocket server = new ServerSocket(PORT, 50,
                InetAddress.getByName("127.0.0.1"))) {
            System.out.println("Сервер запущен: 127.0.0.1:" + PORT);
            while (true) {
                Socket client = server.accept();
                handleClient(client);
            }
        }
    }

    private static void handleClient(Socket client) {
        System.out.println("Подключился клиент: " + client.getRemoteSocketAddress());
        try (Socket socket = client;
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)) {
            String command;
            while ((command = in.readLine()) != null) {
                System.out.println("Получено: " + command);
                if (!command.startsWith("load ")) {
                    out.println("Используйте команду load fname.txt");
                    continue;
                }

                // Запрашивать можно только текстовые файлы из каталога сервера.
                String name = command.substring(5).trim();
                if (!name.matches("[^/\\\\:]+\\.txt")) {
                    out.println("Недопустимое имя файла. Соединение закрыто.");
                    break;
                }
                Path file = DIRECTORY.resolve(name);
                if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                    out.println("Файл " + name + " не найден. Соединение закрыто.");
                    break;
                }
                // Читаем файл построчно и отсылаем содержимое клиенту.
                try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        out.println(line);
                    }
                } catch (IOException e) {
                    out.println("Ошибка чтения файла. Соединение закрыто.");
                    break;
                }
            }
        } catch (IOException e) {
            System.err.println("Ошибка клиента: " + e.getMessage());
        } finally {
            System.out.println("Клиент отключён.");
        }
    }
}
```

# Запуск программы

В обоих терминалах необходимо перейти в каталог lab2. Требуется JDK 8 или новее.

Компиляция:

```bash
javac -encoding UTF-8 TCPServer.java TCPClient.java
```

В первом терминале:

```bash
java TCPServer
```

Во втором терминале:

```bash
java TCPClient
```

В окне клиента ввести `load fname.txt` и нажать PgDn (на Mac — Fn + стрелка вниз). Клиент должен отобразить содержимое файла. Затем ввести `load missing.txt` и нажать PgDn: сервер должен сообщить об отсутствии файла и закрыть соединение. Журнал событий сохраняется в client.log.

# Вывод

Разработаны программы TCP-клиента и TCP-сервера на Java. Реализованы автоматическое подключение, отправка команды по PgDn, получение содержимого текстового файла и журналирование событий.
