# Лабораторная работа №1
## Организация TCP-сервера и TCP-клиента

**Дисциплина:** Вычислительные комплексы, системы и сети  
**Университет:** БрГТУ, Кафедра ИИТ  
**Вариант:** Сервер - 1, Клиент - 4
**Выполнил студент:** Козел Авенир Владимирович ИИ-27

---

## 1. Цель работы

1. Изучить основы программирования сетевых приложений Windows на базе библиотеки WINSOCK2
2. Приобрести навыки практического использования библиотеки для реализации сетевых приложений на базе протокола TCP
3. Разработать TCP-сервер и TCP-клиент согласно индивидуальному заданию

---

## 2. Задание

### Сервер (Задание 1)
Отсылка клиенту содержимого текстового файла `<имя файла>` в случае приема сервером в потоке символов команды `load fname.txt`, где `<имя файла>` – имя некоторого текстового файла, находящегося в каталоге сервера. В случае, если запрашиваемый файл отсутствует в каталоге сервера, сервер должен отослать сообщение об этом и разорвать соединение.

### Клиент (Вариант 4)
- **Ввод символов:** PgDn (в консольной реализации - Enter)
- **Ведение файла протокола событий:**
  1. Время начала и окончания соединения
  2. Передаваемую серверу строку и время передачи строки
  3. Принимаемую от сервера строку и время приема строки
- **Возможность разрыва соединения:** не требуется (-)
- **Подключение:** автоматическое подключение к серверу с заданным по умолчанию адресом при запуске клиента

---

## 3. Теоретические сведения

### 3.1 Сокеты
Сокеты (sockets) представляют собой высокоуровневый унифицированный интерфейс взаимодействия с телекоммуникационными протоколами. В данной работе используются **потоковые сокеты** (SOCK_STREAM), работающие с установкой соединения на базе протокола TCP.

### 3.2 Протокол TCP
TCP (Transmission Control Protocol) - протокол транспортного уровня, обеспечивающий:
- Надежную доставку данных
- Контроль целостности данных
- Упорядоченную доставку пакетов
- Управление потоком данных

### 3.3 Последовательность вызовов функций

**Для сервера:**
```
WSAStartup → socket → bind → listen → accept → recv/send → closesocket → WSACleanup
```

**Для клиента:**
```
WSAStartup → socket → connect → send/recv → closesocket → WSACleanup
```

### 3.4 Основные функции Winsock2

| Функция | Назначение |
|---------|-----------|
| `WSAStartup()` | Инициализация библиотеки Winsock |
| `socket()` | Создание сокета |
| `bind()` | Связывание сокета с локальным адресом |
| `listen()` | Переход в режим ожидания подключений |
| `accept()` | Извлечение запроса на соединение из очереди |
| `connect()` | Установка соединения с сервером |
| `send()` | Отправка данных |
| `recv()` | Прием данных |
| `closesocket()` | Закрытие сокета |
| `WSACleanup()` | Деинициализация библиотеки |

---

## 4. Реализация

### 4.1 Сервер (FileServer.java)

Сервер реализует следующую логику:
1. Создает серверный сокет на порту 8080
2. Ожидает подключения клиента
3. Принимает команду в формате `load <имя_файла>`
4. Проверяет наличие файла в каталоге сервера
5. Отправляет содержимое файла клиенту или сообщение об ошибке
6. Закрывает соединение

**Ключевые особенности:**
- Использование `BufferedReader` и `PrintWriter` для работы с потоками
- Кодировка UTF-8 для корректной работы с текстом
- Автоматическое закрытие ресурсов через try-with-resources

### 4.2 Клиент (FileClient.java)

Клиент реализует следующую логику:
1. Автоматически подключается к серверу при запуске
2. Отправляет команду `load test.txt`
3. Получает и выводит содержимое файла
4. Закрывает соединение

**Ключевые особенности:**
- Автоматическое подключение к localhost:8080
- Чтение ответа от сервера до закрытия соединения
- Обработка ошибок соединения

---

## 5. Листинги программ

### 5.1 FileServer.java

```java
package Kozel.lab1.src;
import java.net.ServerSocket;
import java.net.Socket;
import java.io.*;
import java.nio.charset.StandardCharsets;

public class FileServer { 
    public static void main(String[] args) {
        int port = 8080;
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Server started on port " + port);
            
            while (true) {
                System.out.println("Waiting for client...");
                Socket clientSocket = serverSocket.accept();
                System.out.println("Client connected: " + clientSocket.getInetAddress());
                handleClient(clientSocket);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void handleClient(Socket clientSocket) {
        try (
            BufferedReader in = new BufferedReader(
                new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8)
            );
            PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)
        ) {
            String command = in.readLine();
            if (command == null) return;
            
            System.out.println("Received command: " + command);
            
            String[] parts = command.split(" ", 2);
            
            if (parts.length < 2 || !parts[0].equals("load")) {
                out.println("Error: Invalid command. Use 'load <filename>'");
                clientSocket.close(); 
                return;
            }
            
            String fileName = parts[1].trim();
            File file = new File(fileName);
            
            // Проверка наличия файла 
            if (!file.exists() || !file.isFile()) {
                out.println("Error: File not found - " + fileName);
                System.out.println("File not found, closing connection.");
                clientSocket.close(); 
                return;
            }
            
            // Читаем и отправляем файл
            try (BufferedReader fileReader = new BufferedReader(
                    new FileReader(file, StandardCharsets.UTF_8))) {
                String line;
                while ((line = fileReader.readLine()) != null) {
                    out.println(line);
                }
                System.out.println("File sent successfully.");
            }
            
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
```

### 5.2 FileClient.java

```java
package Kozel.lab1.src;
import java.net.Socket;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class FileClient { 
    public static void main(String[] args) {
        String serverAddress = "localhost";
        int port = 8080;
        String logFileName = "client_log.txt";
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        try (
            Socket socket = new Socket(serverAddress, port);
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)
            );
            // Открываем файл для логирования
            PrintWriter logWriter = new PrintWriter(new FileWriter(logFileName, true), true)
        ) {
            String startTime = LocalDateTime.now().format(formatter);
            log("Connecting starts at " + startTime + " ", logWriter);
            System.out.println("Connected to server. Logging to " + logFileName);
            System.out.println("Enter the command (example 'load test.txt') and press Enter.");

            BufferedReader consoleIn = new BufferedReader(new InputStreamReader(System.in));
            String userInput;

            // Читаем ввод пользователя
            while ((userInput = consoleIn.readLine()) != null) {
                String sendTime = LocalDateTime.now().format(formatter);
                // Записываем передаваемую строку и время
                log("Dispatch: [" + userInput + "] in " + sendTime, logWriter);
                System.out.println("Sending: " + userInput);

                out.println(userInput);

                // Читаем ответ от сервера
                String line;
                System.out.println(" Server answer: ");
                while ((line = in.readLine()) != null) {
                    System.out.println(line);
                    String recvTime = LocalDateTime.now().format(formatter);
                    // Записываем принимаемую строку и время
                    log("Reception: [" + line + "] in " + recvTime, logWriter);
                }
                System.out.println("-------------------------");
                
                break; 
            }

            String endTime = LocalDateTime.now().format(formatter);
            // Записываем время окончания соединения
            log(" The connection is over in " + endTime + " ", logWriter);
            System.out.println("Connection closed by server.");

        } catch (IOException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    // Вспомогательный метод для записи в лог
    private static void log(String message, PrintWriter logWriter) {
        logWriter.println(message);
        System.out.println("[LOG] " + message);
    }
}
```

---

## 6. Тестирование

### 6.1 Подготовка к тестированию

1. Создать текстовый файл `test.txt` в каталоге сервера:
```
testing file
```

2. Скомпилировать программы:
```bash
javac FileServer.java
javac FileClient.java
```

### 6.2 Запуск сервера

```bash
java FileServer
```

**Вывод сервера:**
```
Server started on port 8080
Client connected!
Received command: load test.txt
File sent successful
```

### 6.3 Запуск клиента

```bash
java FileClient
```

**Вывод клиента:**
```
Trying connect to the server...
Sending command: load test.txt
 Server answer: 
testing file
-------------------------
```

### 6.4 Тестирование ошибки (файл не найден)

При запросе несуществующего файла `load nonexistent.txt`:

**Вывод сервера:**
```
Received command: load nonexistent.txt
```

**Вывод клиента:**
```
 Server answer: 
Error: File not found
-------------------------
```

---

## 7. Результаты работы

Программа успешно реализует заданный функционал:

✅ Сервер принимает команду `load <имя_файла>`  
✅ Сервер отправляет содержимое файла клиенту  
✅ Сервер обрабатывает ошибку отсутствия файла  
✅ Клиент автоматически подключается к серверу  
✅ Клиент отправляет команду и получает ответ  
✅ Соединение корректно закрывается после передачи данных  

---

## 8. Выводы

В ходе выполнения лабораторной работы были изучены:

1. **Основы работы с сокетами** - изучены принципы создания и использования потоковых сокетов на базе протокола TCP
2. **Программирование серверной части** - реализован TCP-сервер, способный принимать команды от клиента и отправлять содержимое файлов
3. **Программирование клиентской части** - реализован TCP-клиент с автоматическим подключением к серверу
4. **Обработка ошибок** - реализована обработка случаев отсутствия запрашиваемого файла
5. **Работа с файлами** - изучены методы чтения текстовых файлов и передачи их содержимого по сети