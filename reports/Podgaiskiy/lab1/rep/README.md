<p align="center">Министерство образования Республики Беларусь</p>
<p align="center">Учреждение образования</p>
<p align="center">“Брестский Государственный технический университет”</p>
<p align="center">Кафедра ИИТ</p>
<br><br><br><br><br><br><br>
<p align="center">Лабораторная работа №1</p>
<p align="center">По дисциплине “Аппаратное и Программное обеспечение сетей”</p>
<p align="center">Тема: “Организация TCP – сервера/клиента”</p>
<p align="center">Вариант: 4</p>
<br><br><br><br><br>
<p align="right">Выполнил:</p>
<p align="right">Студент 3 курса</p>
<p align="right">Группы ИИ-27</p>
<p align="right">Подгайский Д.А.</p>
<p align="right">Проверил:</p>
<p align="right">Кулик А.Д.</p>
<br><br><br><br><br>
<p align="center">Брест 2026</p>

# Цель

Реализовать на языке программирования высокого уровня Java клент-серверное взамодействие, организованной через TCP.

# Ход работы

## Код программы 

TCPClient.java
```java
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class TCPClient {
    private static Socket socket;
    private static BufferedReader in;
    private static PrintWriter out;
    private static BufferedReader stdIn;
    
    private static final String LOG_FILE = "client_log.txt";
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static volatile boolean connected = false;

    public static void main(String[] args) {
        stdIn = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        log("Клиент запущен.");
        System.out.println("Клиент запущен. Введите 'connect <адрес> <порт>' для начала работы.");
        System.out.println("Пример: connect 127.0.0.1 666");

        try {
            while (true) {
                System.out.print("> ");
                String input = stdIn.readLine();
                if (input == null) break;
                input = input.trim();
                if (input.isEmpty()) continue;

                if (input.startsWith("connect ")) {
                    if (connected) {
                        System.out.println("Уже подключено. Сначала введите 'disconnect'.");
                        continue;
                    }
                    String[] parts = input.split(" ", 3);
                    if (parts.length < 3) {
                        System.out.println("Использование: connect <адрес> <порт>");
                        continue;
                    }
                    String address = parts[1];
                    int port;
                    try {
                        port = Integer.parseInt(parts[2]);
                    } catch (NumberFormatException e) {
                        System.out.println("Ошибка: некорректный номер порта.");
                        continue;
                    }

                    try {
                        socket = new Socket(address, port);
                        in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                        out = new PrintWriter(socket.getOutputStream(), true);
                        connected = true;
                        
                        log("Соединение установлено с " + address + ":" + port);
                        System.out.println("Успешно подключено к " + address + ":" + port);

                        Thread readThread = new Thread(() -> {
                            try {
                                String serverResponse;
                                while (connected && (serverResponse = in.readLine()) != null) {
                                    System.out.println("Сервер: " + serverResponse);
                                    log("Получено от сервера: '" + serverResponse + "' в " + LocalDateTime.now().format(formatter));
                                    if (serverResponse.contains("Сеанс окончен") || serverResponse.contains("Файл не найден")) {
                                        connected = false;
                                        socket.close();
                                        log("Соединение завершено (по инициативе сервера) в " + LocalDateTime.now().format(formatter));
                                        System.out.println("Соединение разорвано сервером.");
                                    }
                                }
                            } catch (IOException e) {
                                if (connected) {
                                    System.out.println("Соединение потеряно или закрыто сервером.");
                                    log("Соединение потеряно в " + LocalDateTime.now().format(formatter));
                                }
                            } finally {
                                connected = false;
                            }
                        });
                        readThread.setDaemon(true);
                        readThread.start();

                    } catch (IOException e) {
                        System.out.println("Ошибка подключения: " + e.getMessage());
                        log("Ошибка подключения к " + address + ":" + port + " - " + e.getMessage());
                    }
                } else if (input.equalsIgnoreCase("disconnect")) {
                    if (!connected) {
                        System.out.println("Нет активного соединения.");
                        continue;
                    }
                    connected = false;
                    try {
                        socket.close();
                        System.out.println("Соединение разорвано.");
                        log("Соединение завершено (по команде пользователя) в " + LocalDateTime.now().format(formatter));
                    } catch (IOException e) {
                        System.err.println("Ошибка при закрытии соединения: " + e.getMessage());
                    }
                } else if (connected) {
                    out.println(input);
                } else {
                    System.out.println("Нет подключения. Введите 'connect <адрес> <порт>'.");
                }
            }
        } catch (IOException e) {
            System.err.println("Критическая ошибка ввода-вывода: " + e.getMessage());
        } finally {
            if (connected) {
                try {
                    socket.close();
                    log("Соединение завершено (при выходе из программы) в " + LocalDateTime.now().format(formatter));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private static synchronized void log(String message) {
        try (FileWriter fw = new FileWriter(LOG_FILE, StandardCharsets.UTF_8, true);
             BufferedWriter bw = new BufferedWriter(fw);
             PrintWriter pw = new PrintWriter(bw)) {
            String timestamp = LocalDateTime.now().format(formatter);
            pw.println("[" + timestamp + "] " + message);
        } catch (IOException e) {
            System.err.println("Ошибка записи в файл протокола: " + e.getMessage());
        }
    }
}
```

TCPServer.java
```java
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

public class TCPServer {
    static final int PORT = 666;

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("TCP-сервер запущен и ожидает подключений на порту " + PORT + "...");
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Клиент подключился: " + clientSocket.getInetAddress());
                ClientHandler clientHandler = new ClientHandler(clientSocket);
                new Thread(clientHandler).start();
            }
        } catch (IOException e) {
            System.err.println("Ошибка запуска сервера: " + e.getMessage());
        }
    }
}

class ClientHandler implements Runnable {
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;

    public ClientHandler(Socket socket) {
        this.socket = socket;
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            out = new PrintWriter(socket.getOutputStream(), true);
        } catch (IOException e) {
            System.err.println("Ошибка создания потоков: " + e.getMessage());
        }
    }

    @Override
    public void run() {
        try {
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                System.out.println("Получено от клиента: " + inputLine);
                
                if (inputLine.startsWith("find ")) {
                    String[] parts = inputLine.split(" ", 3);
                    if (parts.length < 3) {
                        out.println("Ошибка: используйте формат: find <строка> <имя файла>");
                        continue;
                    }
                    String searchString = parts[1];
                    String filename = parts[2];
                    File file = new File(filename);

                    if (!file.exists() || !file.isFile()) {
                        out.println("Файл не найден: " + filename);
                        out.println("Сеанс окончен.");
                        break;
                    }

                    int count = 0;
                    try (BufferedReader fileReader = new BufferedReader(
                            new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = fileReader.readLine()) != null) {
                            if (line.contains(searchString)) {
                                count++;
                            }
                        }
                    }

                    if (count > 0) {
                        out.println("Найдено строк: " + count + " (поиск: '" + searchString + "' в файле '" + filename + "')");
                    } else {
                        out.println("Строка не найдена в файле. Сеанс окончен.");
                        break;
                    }
                } else {
                    out.println("Неизвестная команда. Доступна команда: find <строка> <имя файла>");
                }
            }
        } catch (IOException e) {
            System.err.println("Ошибка ввода-вывода: " + e.getMessage());
        } finally {
            try {
                socket.close();
                System.out.println("Соединение с клиентом закрыто.");
            } catch (IOException e) {
                System.err.println("Ошибка при закрытии сокета: " + e.getMessage());
            }
        }
    }
}
```

## Результат программы

Server:
```
TCP-сервер запущен и ожидает подключений на порту 666...
Клиент подключился: /127.0.0.1
Получено от клиента: find 6 test.txt
```

Client:
```
Клиент запущен. Введите 'connect <адрес> <порт>' для начала работы.
Пример: connect 127.0.0.1 666
> connect 127.0.0.1 666
Успешно подключено к 127.0.0.1:666
> find 6 test.txt
> Сервер: Найдено строк: 2 (поиск: '6' в файле 'test.txt')
```