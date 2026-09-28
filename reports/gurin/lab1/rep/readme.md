<p align="center">Министерство образования Республики Беларусь</p>
<p align="center">Учреждение образования</p>
<p align="center">“Брестский Государственный технический университет”</p>
<p align="center">Кафедра ИИТ</p>
<br><br><br><br><br><br><br>
<p align="center">Лабораторная работа №1</p>
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
Подсчет контрольной суммы ASCII символов для каждой из последовательностей из 10 символовпотока клиента и отсылка контрольной суммы после приема каждой из цепочек клиенту

# Структура программы
```
reports/gurin/lab1/
├── rep/
│   └── README.md
└── src/
    ├── FileServer.java
    ├── FileClient.java
    └── server.log (создаётся автоматически)
```
# Код программы
## Клиентская часть
```java
import java.io.*;
import java.net.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Scanner;

public class TCPclient {
    private static final String LOG_FILE = "client.log";
    private static final DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        Socket socket = null;
        BufferedReader in = null;
        PrintWriter out = null;

        try {
            System.out.println("Введите команду: connect <host> <port>");
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine().trim();
                if (line.startsWith("connect ")) {
                    String[] parts = line.split("\\s+");
                    if (parts.length == 3) {
                        String host = parts[1];
                        int port = Integer.parseInt(parts[2]);
                        socket = new Socket(host, port);
                        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                        out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())), true);
                        System.out.println("Подключено к " + host + ":" + port);
                        break;
                    } else {
                        System.out.println("Использование: connect <host> <port>");
                    }
                } else {
                    System.out.println("Неизвестная команда. Используйте: connect <host> <port>");
                }
            }

            if (socket != null) {
                System.out.println("Вводите строки для отправки (пустая строка — выход):");
                while (scanner.hasNextLine()) {
                    String msg = scanner.nextLine();
                    if (msg.isEmpty()) break;

                    
                    log("Sent: " + msg + " at " + LocalDateTime.now().format(fmt));

                    out.println(msg);               
                    String response = in.readLine(); 
                    if (response == null) {
                        System.out.println("Сервер закрыл соединение.");
                        break;
                    }
                    System.out.println("Ответ сервера: " + response);
                }
            }
        } catch (IOException e) {
            System.err.println("Ошибка клиента: " + e);
        } finally {
            try {
                if (socket != null) socket.close();
                if (scanner != null) scanner.close();
            } catch (IOException e) { /* ignore */ }
        }
    }

    private static void log(String message) {
        try (FileWriter fw = new FileWriter(LOG_FILE, true);
             BufferedWriter bw = new BufferedWriter(fw);
             PrintWriter out = new PrintWriter(bw)) {
            out.println(message);
        } catch (IOException e) {
            System.err.println("Не удалось записать в лог: " + e);
        }
    }
}
```
## Серверная часть
```java
import java.io.*;
import java.net.*;

public class TCPserver {
    public static final int PORT = 8080;

    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = new ServerSocket(PORT);
        System.out.println("Server started on port " + PORT);
        try {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("New client: " + clientSocket);
                new Thread(new ClientHandler(clientSocket)).start();
            }
        } finally {
            serverSocket.close();
        }
    }
}

class ClientHandler implements Runnable {
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;

    public ClientHandler(Socket socket) throws IOException {
        this.socket = socket;
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())), true);
    }

    @Override
    public void run() {
        try {
            int totalChars = 0;
            char[] buffer = new char[48];
            int bufIndex = 0;
            int ch;
            while ((ch = in.read()) != -1) {
                char c = (char) ch;
                
                if (c == '\n' || c == '\r') continue;

                
                if (c == '#') {
                    out.println("Session ended by server.");
                    break;
                }

                buffer[bufIndex++] = c;
                totalChars++;

                if (bufIndex == 48) {
                    int checksum = 0;
                    for (char x : buffer) checksum += (int) x;
                    out.println("Checksum: " + checksum + ", Total chars: " + totalChars);
                    bufIndex = 0; 
                }
            }
        } catch (IOException e) {
            System.err.println("IO error: " + e);
        } finally {
            try { socket.close(); } catch (IOException e) { /* ignore */ }
            System.out.println("Client disconnected.");
        }
    }
}
```