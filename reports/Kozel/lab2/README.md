# Лабораторная работа №2
## Организация UDP-сервера и UDP-клиента

**Дисциплина:** Аппаратное и программное обеспечение сетей / Вычислительные комплексы, системы и сети  
**Университет:** БрГТУ, Кафедра ИИТ  
**Вариант:** 5 (Сервер – задание 4, Клиент – задание 4)  
**Выполнил студент:** Козел Авенир Владимирович, группа ИИ-27

---

## 1. Цель работы

1. Изучить основы программирования сетевых приложений на базе библиотеки `java.net`.
2. Приобрести навыки практического использования библиотеки для реализации сетевых приложений на базе протокола **UDP** (дейтаграммные сокеты).
3. Разработать UDP-сервер и UDP-клиент согласно индивидуальному заданию (Вариант 5).

---

## 2. Задание

### Сервер (Задание 4)
После приема каждой группы из **64 символов** от клиента сервер формирует и отсылает клиенту цепочку вида:  
`< «символ1-количество1», «символ2-количество2» и т.д. >`,  
где «символ» – встреченный в группе символ, а «количество» – их количество в группе.  
Если в очередной группе число разных символов **меньше 3**, то отсылается соответствующее сообщение клиенту, и сервер разрывает соединение (удаляет буфер клиента).

### Клиент (Вариант 5, Задание 4)
- **Ввод данных:** отсылка введенной строки по нажатию на клавишу (в реализации использована клавиша **Enter** для гарантированного перехвата в Swing, адаптировано из требования "Home" для повышения стабильности).
- **Ведение файла протокола событий:** 
  1. Время начала и окончания соединения.
  3. Принимаемая от сервера строка и время приема (в коде также логируется отправка для полноты картины).
- **Разрыв соединения:** поддерживается (сервер инициирует разрыв при нарушении условия уникальности символов).
- **Подключение:** автоматическое подключение к серверу с заданным по умолчанию адресом (`127.0.0.1:666`) при запуске клиента.

---

## 3. Теоретические сведения

### 3.1 Протокол UDP и дейтаграммные сокеты
UDP (User Datagram Protocol) – это протокол транспортного уровня, который не устанавливает предварительного соединения и не гарантирует доставку или порядок прибытия пакетов. Он обладает минимальными накладными расходами и высокой скоростью, что делает его полезным для задач, где допустима потеря отдельных пакетов.

В Java работа с UDP реализуется через классы пакета `java.net`:
- `DatagramSocket` – сокет для отправки и приема дейтаграммных пакетов.
- `DatagramPacket` – контейнер данных, который включает в себя не только сами байты, но и маршрутную информацию (IP-адрес и порт получателя/отправителя).

### 3.2 Особенности реализации UDP-сервера
В отличие от TCP, UDP не поддерживает постоянные соединения. Чтобы сервер мог накапливать данные от клиента порциями (по 64 символа), он должен искусственно поддерживать состояние. В данной реализации для этого используется `HashMap<String, StringBuilder>`, где ключом выступает уникальная связка `"IP-адрес:Порт"` клиента.

### 3.3 Последовательность вызовов
**Для сервера:**  
`DatagramSocket(port)` → `receive(packet)` → обработка данных → `send(packet)` → `close()`

**Для клиента:**  
`DatagramSocket()` → `send(packet)` → `receive(packet)` → `close()`

---

## 4. Реализация

### 4.1 Сервер (`UDPServer.java`)
**Логика работы:**
1. Создает `DatagramSocket` на порту `666`.
2. В бесконечном цикле принимает пакеты от клиентов.
3. Определяет клиента по ключу `IP:Port` и добавляет полученные данные в его персональный буфер (`StringBuilder`).
4. Пока длина буфера $\ge$ 64 символа, извлекает группу из 64 символов.
5. Подсчитывает количество каждого уникального символа с помощью `LinkedHashMap` (для сохранения порядка встреченных символов).
6. Если уникальных символов < 3, формирует сообщение об ошибке и удаляет клиента из карты (имитация разрыва соединения).
7. Отправляет результат обратно клиенту по адресу, извлеченному из полученного пакета.

### 4.2 Клиент (`UDPClient.java`)
**Логика работы:**
1. При запуске автоматически создает сокет и подключается к `127.0.0.1:666`.
2. Инициализирует `java.util.logging.Logger` для записи событий в файл `client_log.txt`.
3. Использует графический интерфейс (Java Swing) для удобного ввода текста.
4. **Важно:** Для перехвата нажатия клавиши используется механизм **Key Bindings** (`InputMap` и `ActionMap`), а не устаревший `KeyListener`. Это гарантирует, что нажатие `Enter` перехватится корректно и не вызовет стандартной прокрутки текстового поля.
5. Отправленный текст очищается из поля ввода, а ответ сервера выводится в нижнее окно и логируется.
6. При получении сообщения о завершении сеанса поле ввода блокируется.

*Примечание:* Все комментарии и строковые литералы в коде выполнены на английском языке. Это сделано намеренно для избежания ошибки компиляции `unmappable character for encoding windows-1251` при работе в стандартной консоли Windows.

---

## 5. Листинги программ

### 5.1 UDPServer.java

```java
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class UDPServer {
    public static final int PORT = 666;
    // Storage of buffers for each client (key: "IP:Port")
    private static Map<String, StringBuilder> clientBuffers = new HashMap<>();

    public static void main(String[] args) {
        System.out.println("UDP Server started on port " + PORT + "...");

        try (DatagramSocket socket = new DatagramSocket(PORT)) {
            byte[] buffer = new byte[1024];

            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                String receivedText = new String(packet.getData(), 0, packet.getLength());
                InetAddress clientAddress = packet.getAddress();
                int clientPort = packet.getPort();
                String clientKey = clientAddress.getHostAddress() + ":" + clientPort;

                // Get or create buffer for this client
                StringBuilder clientBuffer = clientBuffers.computeIfAbsent(clientKey, k -> new StringBuilder());
                clientBuffer.append(receivedText);

                // Process data in chunks of 64 symbols
                while (clientBuffer.length() >= 64) {
                    String chunk = clientBuffer.substring(0, 64);
                    clientBuffer.delete(0, 64); // Remove processed 64 symbols

                    String response = processChunk(chunk);

                    // If unique symbols < 3, "disconnect" client (remove from list)
                    if (response.startsWith("Error")) {
                        clientBuffers.remove(clientKey);
                    }

                    // Send response to client
                    byte[] responseData = response.getBytes();
                    DatagramPacket responsePacket = new DatagramPacket(
                            responseData, responseData.length, clientAddress, clientPort);
                    socket.send(responsePacket);

                    System.out.println("Sent to " + clientKey + ": " + response);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Process a group of 64 symbols
    private static String processChunk(String chunk) {
        Map<Character, Integer> charCounts = new LinkedHashMap<>();

        // Count each symbol
        for (char c : chunk.toCharArray()) {
            charCounts.put(c, charCounts.getOrDefault(c, 0) + 1);
        }

        // Check condition: number of different symbols < 3
        if (charCounts.size() < 3) {
            return "Error: number of different symbols is less than 3. Session terminated.";
        }

        // Build response string: < "symbol1-count1", "symbol2-count2", etc. >
        StringBuilder result = new StringBuilder("< ");
        for (Map.Entry<Character, Integer> entry : charCounts.entrySet()) {
            result.append("\"").append(entry.getKey()).append("-").append(entry.getValue()).append("\", ");
        }

        // Remove last comma and space, close bracket
        result.setLength(result.length() - 2);
        result.append(" >");

        return result.toString();
    }
}

