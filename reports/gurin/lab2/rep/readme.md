<p align="center">Министерство образования Республики Беларусь</p>
<p align="center">Учреждение образования</p>
<p align="center">“Брестский Государственный технический университет”</p>
<p align="center">Кафедра ИИТ</p>
<br><br><br><br><br><br><br>
<p align="center">Лабораторная работа №2</p>
<p align="center">По дисциплине “Аппаратное и Программное обеспечение сетей”</p>
<p align="center">Тема: “Организация TCP – сервера/клиента”</p>
<p align="center">Вариант: 6</p>
<br><br><br><br><br>
<p align="right">Выполнил:</p>
<p align="right">Студент 3 курса</p>
<p align="right">Группы ИИ-27</p>
<p align="right">Гурин М.Д..</p>
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
После приема каждой группы из 48 символов сервер отсылает подтверждение в виде контрольнойсуммы ASCII-кодов символов группы и количество символов, принятых сервером от началасеанса. Если в потоке принятых символов встречается последовательность «~#~», то серверотсылает сообщение об окончании сеанса и разрывает соединение.

# Задания для реализации TCP клиента
Подсчет контрольной суммы ASCII символов для каждой из последовательностей из 10 символовпотока клиента и отсылка контрольной суммы после приема каждой из цепочек клиент

# Структура программы
```
reports/gurin/lab2/
├── rep/
│   └── README.md
└── src/
    ├── UDPServer.java
    ├── UDPClient.java
    └── file.log (создаётся автоматически)
```
# Код программы
## Клиентская часть

```java
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;

public class UDPClient {
    private static DatagramSocket socket = null;
    private static InetAddress serverAddress = null;
    private static int serverPort = -1;
    private static Thread listenerThread = null;
    private static boolean isRunning = true;

    private static final SimpleDateFormat SDF = new SimpleDateFormat("HH:mm:ss.SSS");
    private static final String LOG_FILE = "server.log";

    public static void main(String[] args) {
        System.out.println("connect <address> <port>");
        System.out.println("disconnect <address> <port>");
        System.out.println("exit");

        BufferedReader consoleReader = new BufferedReader(new InputStreamReader(System.in));

        try {
            while (isRunning) {
                System.out.print("> ");
                String input = consoleReader.readLine();
                if (input == null) break;
                String trimmed = input.trim();
                if (trimmed.isEmpty()) continue;

                if (trimmed.equalsIgnoreCase("exit")) {
                    if (socket != null && !socket.isClosed()) socket.close();
                    break;
                }

                if (trimmed.startsWith("connect ")) {
                    String[] parts = trimmed.split("\\s+");
                    if (parts.length == 3) connect(parts[1], parts[2]);
                    else System.out.println("Формат: connect <адрес> <порт>");
                } else if (trimmed.startsWith("disconnect ")) {
                    String[] parts = trimmed.split("\\s+");
                    if (parts.length == 3) disconnect(parts[1], parts[2]);
                    else System.out.println("Формат: disconnect <адрес> <порт>");
                } else {
                    sendText(input);
                }
            }
        } catch (IOException e) {
            System.err.println("Ошибка ввода-вывода: " + e.getMessage());
        }
    }

    private static void connect(String host, String portStr) {
        try {
            InetAddress addr = InetAddress.getByName(host);
            int port = Integer.parseInt(portStr);

            if (socket != null && !socket.isClosed()) socket.close();

            serverAddress = addr;
            serverPort = port;
            socket = new DatagramSocket();

            listenerThread = new Thread(UDPClient::receiveLoop);
            listenerThread.setDaemon(true);
            listenerThread.start();

            logToFile("Установлено соединение с " + addr.getHostAddress() + ":" + port);
            System.out.println("Подключено к " + addr.getHostAddress() + ":" + port);
        } catch (Exception e) {
            logToFile("Ошибка подключения: " + e.getMessage());
            System.err.println("Ошибка подключения: " + e.getMessage());
        }
    }

    private static void disconnect(String host, String portStr) {
        try {
            int port = Integer.parseInt(portStr);
            if (serverAddress == null || serverPort != port ||
                    !serverAddress.getHostAddress().equals(InetAddress.getByName(host).getHostAddress())) {
                System.out.println("Указанный адрес/порт не совпадает с текущим соединением.");
                return;
            }
            logToFile("Разрыв соединения с " + host + ":" + port);
            System.out.println("Разрыв соединения с " + host + ":" + port);
            if (socket != null && !socket.isClosed()) socket.close();
            serverAddress = null;
            serverPort = -1;
        } catch (Exception e) {
            logToFile("Ошибка разрыва: " + e.getMessage());
            System.err.println("Ошибка разрыва: " + e.getMessage());
        }
    }

    private static void sendText(String text) {
        if (serverAddress == null || serverPort <= 0 || socket == null || socket.isClosed()) {
            System.out.println("Сначала подключитесь: connect <адрес> <порт>");
            return;
        }
        try {
            byte[] data = text.getBytes(StandardCharsets.UTF_8);
            DatagramPacket packet = new DatagramPacket(data, data.length, serverAddress, serverPort);
            socket.send(packet);
            logToFile("Отправлено (" + data.length + " б): " + text);
            System.out.println("Отправлено (" + data.length + " б): " + text);
        } catch (IOException e) {
            logToFile("Ошибка отправки: " + e.getMessage());
            System.err.println("Ошибка отправки: " + e.getMessage());
        }
    }

    private static void receiveLoop() {
        byte[] buf = new byte[2048];
        while (true) {
            try {
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                socket.receive(packet);
                String response = new String(packet.getData(), packet.getOffset(), packet.getLength(), StandardCharsets.UTF_8);
                String clean = response.replace("\n", " | ").trim();
                logToFile("Принято: " + clean);
                System.out.println("\n[Ответ] " + clean);
            } catch (SocketException e) {
                break;
            } catch (IOException e) {
                if (socket != null && !socket.isClosed()) {
                    logToFile("Ошибка приёма: " + e.getMessage());
                }
                break;
            }
        }
    }

    private static synchronized void logToFile(String msg) {
        String time = SDF.format(new Date());
        String entry = "[" + time + "] " + msg;
        try (PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(LOG_FILE, true)))) {
            out.println(entry);
        } catch (IOException e) {
            System.err.println("Ошибка записи в лог: " + e.getMessage());
        }
    }
}
```
## Серверная часть
```java
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class UDPServer {
    public static final int PORT = 666;

    private static class Session {
        StringBuilder group = new StringBuilder();
        StringBuilder tail = new StringBuilder();
        int totalChars = 0;
        boolean finished = false;
    }

    private static final Map<String, Session> sessions = new HashMap<>();

    public static void main(String[] args) throws IOException {
        try (DatagramSocket socket = new DatagramSocket(PORT)) {
            System.out.println("Server started on port " + PORT);
            byte[] buf = new byte[2048];

            while (true) {
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                socket.receive(packet);

                String clientKey = packet.getAddress().getHostAddress() + ":" + packet.getPort();
                String received = new String(packet.getData(), packet.getOffset(), packet.getLength(), StandardCharsets.UTF_8);
                System.out.println("From " + clientKey + " -> " + received);

                Session session = sessions.computeIfAbsent(clientKey, k -> new Session());

                if (session.finished) {
                    send(socket, packet, "Сеанс завершён\n");
                    continue;
                }

                StringBuilder response = new StringBuilder();
                boolean sessionEnded = false;

                for (int i = 0; i < received.length(); i++) {
                    char c = received.charAt(i);
                    session.totalChars++;

                    session.tail.append(c);
                    if (session.tail.length() > 3) {
                        session.tail.deleteCharAt(0);
                    }
                    if (session.tail.length() == 3 && session.tail.toString().equals("~#~")) {
                        response.append("Конец сеанса\n");
                        sessionEnded = true;
                        break;
                    }

                    session.group.append(c);
                    if (session.group.length() == 48) {
                        int checksum = 0;
                        for (int j = 0; j < session.group.length(); j++) {
                            checksum += (int) session.group.charAt(j);
                        }
                        response.append("Контрольная сумма ASCII: ").append(checksum)
                                .append(", Всего символов: ").append(session.totalChars)
                                .append("\n");
                        System.out.println("checksum=" + checksum + ", total=" + session.totalChars);
                        session.group.setLength(0);
                    }
                }

                if (response.length() > 0) {
                    send(socket, packet, response.toString());
                }

                if (sessionEnded) {
                    session.finished = true;
                }
            }
        }
    }

    private static void send(DatagramSocket socket, DatagramPacket clientPacket, String message) throws IOException {
        byte[] data = message.getBytes(StandardCharsets.UTF_8);
        DatagramPacket response = new DatagramPacket(data, data.length, clientPacket.getAddress(), clientPacket.getPort());
        socket.send(response);
    }
}
```