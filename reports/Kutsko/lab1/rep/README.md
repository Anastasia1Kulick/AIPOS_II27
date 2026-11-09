<p align="center">Министерство образования Республики Беларусь</p>
<p align="center">Учреждение образования</p>
<p align="center">«Брестский государственный технический университет»</p>
<p align="center">Кафедра ИИТ</p>
<br><br><br><br><br>
<p align="center"><b>Лабораторная работа №1</b></p>
<p align="center">По дисциплине «Аппаратное и Программное обеспечение сетей»</p>
<p align="center">Тема: «Организация TCP – сервера/клиента»</p>
<p align="center"><b>Вариант: Сервер – 6, Клиент – 3</b></p>
<br><br><br>
<p align="right"><b>Выполнил:</b><br>
Студент 3 курса<br>
Группы ИИ-27<br>
Куцко Радислав Русланович</p>
<p align="right"><b>Проверил:</b><br>
Кулик А.Д.</p>
<br><br><br>
<p align="center">Брест 2026</p>

---

# Цель работы
Реализовать на языке программирования высокого уровня Java клиент-серверное взаимодействие, организованное через протокол TCP, в соответствии с индивидуальным вариантом, с использованием многопоточности и механизмов логирования событий.

# Задание на выполнение

### Сервер (Вариант 6)
1. Сервер принимает поток символов от клиента.
2. После приема каждых **48 символов** отправляет подтверждение, содержащее: контрольную сумму ASCII-кодов группы + общее количество символов с начала сеанса.
3. Если встречается последовательность `~#~`, сервер отправляет сообщение о завершении и разрывает соединение.

### Клиент (Вариант 3)
1. Подключение к серверу только по команде `connect <адрес> <порт>` (без автоподключения при старте).
2. Ввод и отправка строк данных.
3. Ведение лога событий (время и текст отправленных/полученных строк).
4. Поддержка команды `disconnect` для принудительного разрыва соединения.

---

# Ход работы и технические решения

### 1. Обоснование выбора языка
Для реализации был выбран язык Java по следующим причинам:
1. **Безопасность и управление памятью:** Нет необходимости вручную управлять освобождением буферов или указателями, что исключает утечки памяти, характерные для C/C++.
2. **Высокоуровневая работа с сетью:** В Java используются интуитивно понятные объекты (`ServerSocket`, `Socket`, `InputStream`, `OutputStream`), в то время как в C/C++ требуется работа с низкоуровневыми функциями (`socket()`, `bind()`, `WSAStartup()`), что делает код платформозависимым.
3. **Встроенное логирование:** Пакет `java.util.logging` позволяет легко реализовать протоколирование с временными метками без подключения сторонних библиотек.

### 2. Особенности реализации клиента (Обработка клавиши End)
В задании указано: *"Ввод символов с отсылкой введенной строки по нажатию на клавишу End"*.  
**Важное примечание:** Стандартная консоль Java (`System.in`) работает в буферизованном построчном режиме и не имеет кроссплатформенного способа перехватить нажатие служебных клавиш (таких как `End`, `F1` и т.д.) без предварительного нажатия `Enter`. Поэтому в консольных сетевых приложениях стандартом де-факто является отправка по нажатию `Enter` (метод `Scanner.nextLine()`). Данное ограничение среды выполнения (JVM / терминала) было учтено при разработке.

### 3. Алгоритм работы сервера
- Сервер читает поток **посимвольно** (`in.read()`), чтобы символы переноса строк (`\r`, `\n`) также участвовали в подсчете 48-символьной группы и контрольной суммы.
- Контрольная сумма вычисляется как сумма числовых значений (ASCII/ISO-8859-1) принятых символов.
- Для корректного отлова маркера `~#~` используется **"скользящее окно"** (`StringBuilder recent`), что гарантирует обнаружение последовательности, даже если эти три символа пришли в разных сетевых пакетах.

### 4. Логирование
Используется стандартный `java.util.logging`. В метод `FileHandler` явно передана кодировка `UTF-8` (`fh.setEncoding("UTF-8")`), что гарантирует корректное отображение русских букв в файлах `client_log.txt` и `server_log.txt`.

### 5. Код TaskServer
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.logging.*;

public class TaskServer {
    private static final int PORT = 666;
    private static final Logger logger = Logger.getLogger(TaskServer.class.getName());

    public static void main(String[] args) {
        setupLogger("server_log.txt");
        logger.info("Сервер запущен и ожидает подключений на порту " + PORT);
        System.out.println("Сервер запущен на порту " + PORT);

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                logger.info("Новое подключение от: " + clientSocket.getInetAddress());
                new Thread(new ClientHandler(clientSocket)).start();
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Ошибка сервера", e);
        }
    }

    static class ClientHandler implements Runnable {
        private final Socket socket;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            int groupCount = 0;       
            int groupChecksum = 0;    
            int totalCount = 0;       
            StringBuilder recent = new StringBuilder(); 

            try (Reader in = new InputStreamReader(socket.getInputStream(), StandardCharsets.ISO_8859_1);
                 PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

                int c;
                while ((c = in.read()) != -1) {
                    char ch = (char) c;
                    totalCount++;
                    groupCount++;
                    groupChecksum += ch; 

                    recent.append(ch);
                    if (recent.length() > 3) {
                        recent.delete(0, recent.length() - 3);
                    }

                    if (recent.toString().contains("~#~")) {
                        String endMsg = "SESSION_END: Обнаружена последовательность ~#~. Сеанс завершен.";
                        out.println(endMsg);
                        logger.info("Сеанс завершен маркером ~#~. Всего символов: " + totalCount);
                        break;
                    }

                    if (groupCount == 48) {
                        String ackMsg = "ACK: Checksum=" + groupChecksum + ", TotalReceived=" + totalCount;
                        out.println(ackMsg);
                        logger.info("Отправлено подтверждение: " + ackMsg);
                        groupCount = 0;
                        groupChecksum = 0;
                    }
                }
            } catch (IOException e) {
                logger.log(Level.WARNING, "Клиент отключился или ошибка чтения", e);
            } finally {
                try { socket.close(); } catch (IOException e) { /* ignore */ }
            }
        }
    }

    private static void setupLogger(String fileName) {
        try {
            FileHandler fh = new FileHandler(fileName, true);
            fh.setEncoding("UTF-8"); 
            fh.setFormatter(new SimpleFormatter());
            logger.addHandler(fh);
            logger.setUseParentHandlers(false); 
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

### 6. Код TaskClient
import java.io.*;
import java.net.*;
import java.util.Scanner;
import java.util.logging.*;

public class TaskClient {
    private static final Logger logger = Logger.getLogger(TaskClient.class.getName());
    private static Socket socket;
    private static PrintWriter out;
    private static BufferedReader in;
    private static boolean isConnected = false;

    public static void main(String[] args) {
        setupLogger("client_log.txt");
        Scanner scanner = new Scanner(System.in);
        
        System.out.println("Клиент запущен.");
        System.out.println("Введите 'connect <IP> <PORT>' для подключения.");
        System.out.println("Введите 'disconnect' для отключения.");

        while (true) {
            System.out.print("> ");
            String command = scanner.nextLine();

            if (command.startsWith("connect ")) {
                String[] parts = command.split(" ");
                if (parts.length == 3) {
                    try {
                        connect(parts[1], Integer.parseInt(parts[2]));
                    } catch (NumberFormatException e) {
                        System.out.println("Неверный формат порта.");
                    }
                } else {
                    System.out.println("Формат: connect <IP> <PORT>");
                }
            } else if (command.equalsIgnoreCase("disconnect")) {
                disconnect();
            } else if (isConnected) {
                sendData(command);
            } else {
                System.out.println("Соединение не установлено. Используйте 'connect <IP> <PORT>'.");
            }
        }
    }

    private static void connect(String ip, int port) {
        try {
            socket = new Socket(ip, port);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            isConnected = true;
            logger.info("Успешное подключение к " + ip + ":" + port);
            System.out.println("Соединение установлено.");

            new Thread(() -> {
                try {
                    String response;
                    while ((response = in.readLine()) != null) {
                        System.out.println("Сервер: " + response);
                        logger.info("Получено от сервера: " + response);
                        
                        if (response.startsWith("SESSION_END")) {
                            disconnect();
                            break;
                        }
                    }
                } catch (IOException e) {
                    if (isConnected) {
                        System.out.println("Соединение потеряно.");
                        isConnected = false;
                    }
                }
            }).start();
        } catch (IOException e) {
            System.out.println("Ошибка подключения: " + e.getMessage());
            logger.warning("Ошибка подключения: " + e.getMessage());
        }
    }

    private static void sendData(String data) {
        out.println(data);
        logger.info("Отправлено серверу: " + data);
    }

    private static void disconnect() {
        if (isConnected) {
            try {
                socket.close();
            } catch (IOException e) { /* ignore */ }
            isConnected = false;
            System.out.println("Соединение разорвано.");
            logger.info("Соединение разорвано пользователем или сервером.");
        } else {
            System.out.println("Соединение уже разорвано.");
        }
    }

    private static void setupLogger(String fileName) {
        try {
            FileHandler fh = new FileHandler(fileName, true);
            fh.setEncoding("UTF-8"); 
            fh.setFormatter(new SimpleFormatter());
            logger.addHandler(fh);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
```