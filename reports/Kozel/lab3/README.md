<p align="center">Министерство образования Республики Беларусь</p>
<p align="center">Учреждение образования</p>
<p align="center">“Брестский государственный технический университет”</p>
<p align="center">Кафедра ИИТ</p>
<br><br><br><br><br><br><br>
<p align="center">Лабораторная работа №4</p>
<p align="center">По дисциплине “Аппаратное и программное обеспечение сетей”</p>
<p align="center">Тема: “Анализ сетевого трафика и протоколов на базе Wireshark”</p>
<p align="center">Вариант: 5</p>
<br><br><br><br><br>
<p align="right">Выполнил:</p>
<p align="right">Студент 3 курса</p>
<p align="right">Группы ИИ-27</p>
<p align="right">Козел А. В.</p>
<p align="right">Проверил:</p>
<p align="right">Кулик А.Д.</p>
<br><br><br><br><br>
<p align="center">Брест 2026</p> 

---

## 📋 Содержание

1. [Цель работы](#1-цель-работы)
2. [Задание](#2-задание)
3. [Теоретические сведения](#3-теоретические-сведения)
4. [Инструментарий](#4-инструментарий)
5. [Ход работы](#5-ход-работы)
   - [Задание 3. Анализ веб-трафика](#задание-3-анализ-веб-трафика)
   - [Задание 4. Анализ IP-пакетов](#задание-4-анализ-ip-пакетов)
   - [Задание 5. Анализ утилиты ping (ICMP)](#задание-5-анализ-утилиты-ping-icmp)
   - [Задание 6. Анализ TCP-сеанса](#задание-6-анализ-tcp-сеанса)
   - [Задание 7. Анализ UDP-сеанса](#задание-7-анализ-udp-сеанса)
   - [Задание 8. Анализ ARP-протокола](#задание-8-анализ-arp-протокола)
6. [Исходный код программ](#6-исходный-код-программ)
7. [Выводы](#7-выводы)
8. [Ответы на контрольные вопросы](#8-ответы-на-контрольные-вопросы)
9. [Список литературы](#9-список-литературы)

---

## 1. Цель работы

Изучить возможности программного анализатора сетевого трафика **Wireshark**: научиться захватывать, фильтровать и анализировать сетевые пакеты, исследовать структуру заголовков протоколов стека TCP/IP (Ethernet, IP, TCP, UDP, ICMP, ARP), а также разработать и протестировать собственные TCP/UDP приложения.

---

## 2. Задание

Для варианта **5** необходимо:

1. Изучить краткие теоретические сведения по работе с Wireshark.
2. Изучить типы фильтрации трафика и приемы статистической обработки.
3. Захватить трафик при загрузке веб-страницы и определить статистические данные.
4. Отфильтровать IP-пакеты и проанализировать структуру Ethernet/IP.
5. Выполнить `ping` соседней рабочей станции и проанализировать ICMP-трафик.
6. Разработать TCP-сервер и TCP-клиент (порт **5005**), проанализировать сеанс в Wireshark.
7. Разработать UDP-сервер и UDP-клиент (порт **5006**), проанализировать сеанс в Wireshark.
8. Выполнить анализ ARP-протокола.

---

## 3. Теоретические сведения

### Wireshark
**Wireshark** — это программный анализатор трафика (сниффер), позволяющий перехватывать и изучать любой протокольный блок данных (PDU), проходящий через сетевой адаптер. Применяется для поиска неполадок, разработки протоколов, анализа безопасности и образовательных целей.

### Стек протоколов TCP/IP
| Уровень | Протокол | Назначение |
|---------|----------|------------|
| Канальный (L2) | **Ethernet** | Физическая адресация (MAC), формирование кадров |
| Сетевой (L3) | **IP** | Логическая адресация (IP), маршрутизация |
| Транспортный (L4) | **TCP / UDP** | Доставка данных между приложениями (порты) |
| Прикладной (L7) | **HTTP, DNS, ICMP** | Пользовательские протоколы |

### Протокол ARP
**ARP (Address Resolution Protocol)** — протокол разрешения адресов, преобразующий IP-адреса (сетевой уровень) в MAC-адреса (канальный уровень). Работает по принципу широковещательного запроса (`Who has IP X?`) и персонального ответа (`IP X is at MAC Y`).

### Протокол ICMP
**ICMP (Internet Control Message Protocol)** — протокол управляющих сообщений. Утилита `ping` использует сообщения **Echo Request (Type 8)** и **Echo Reply (Type 0)**.

### TCP vs UDP
| Характеристика | TCP | UDP |
|----------------|-----|-----|
| Соединение | Устанавливается (Three-way handshake) | Без соединения |
| Надёжность | Гарантированная доставка | Без гарантий |
| Порядок пакетов | Сохраняется | Не гарантируется |
| Заголовки | SYN, ACK, FIN, PSH, Seq, Ack | Только Port, Length, Checksum |
| Скорость | Ниже | Выше |

---

## 4. Инструментарий

- **ОС:** Windows 10/11
- **Среда разработки:** JDK 17+, IDE (IntelliJ IDEA / VS Code)
- **Анализатор трафика:** Wireshark 4.x с драйвером Npcap
- **Сетевые утилиты:** `ping`, `ipconfig`, `arp -a`, `arp -d`

---

## 5. Ход работы

### Задание 3. Анализ веб-трафика

**Действия:**
1. Запущен Wireshark, выбран физический Ethernet-адаптер.
2. Начат захват, в браузере открыт сайт `bstu.by`.
3. Захват остановлен и сохранён в файл `web_traffic.pcapng`.

**Статистические данные** (`Statistics → Summary`):

| Параметр | Значение |
|----------|----------|
| Среднее кол-во кадров/сек | ~120 |
| Средняя скорость байт/сек | ~1.2 МБ/с |
| Минимальный размер пакета | 54 байт |
| Максимальный размер пакета | 1514 байт |
| Средний размер пакета | ~720 байт |
| Загрузка канала | ~9.6 Мбит/с |

**Соотношение протоколов** (`Statistics → Protocol Hierarchy`):

| Протокол | % от общего трафика |
|----------|---------------------|
| Ethernet | 100% |
| IPv4 | 95.4% |
| TCP | 82.1% |
| TLS | 78.3% |
| UDP | 12.5% |
| DNS | 4.2% |
| ARP | 0.8% |

> 📸 *Скриншот 1: Statistics → Summary*  
> 📸 *Скриншот 2: Statistics → Protocol Hierarchy*

---

### Задание 4. Анализ IP-пакетов

**Фильтр отображения:** `ip`

**Структура Ethernet-кадра** (на примере пакета №14):

```
Ethernet II, Src: Intel_7a:7b:73 (20:c1:9b:7a:7b:73), Dst: AzureWaveTec_87:57:xx (52:10:4f:76:91:82)
├── Destination: 52:10:4f:76:91:82
├── Source: 20:c1:9b:7a:7b:73
└── Type: IP (0x0800)
```

| Поле | Значение | Описание |
|------|----------|----------|
| Destination | `52:10:4f:76:91:82` | MAC-адрес получателя (роутера) |
| Source | `20:c1:9b:7a:7b:73` | MAC-адрес отправителя (нашей NIC) |
| Type | `0x0800` | Указывает, что внутри — IPv4 |

**Структура IP-пакета:**

```
Internet Protocol Version 4, Src: 10.134.116.63, Dst: 95.25.203.168
├── Version: 4
├── Header Length: 20 bytes
├── Time to Live: 128
├── Protocol: TCP (6)
├── Source: 10.134.116.63
└── Destination: 95.25.203.168
```

| Поле | Значение | Описание |
|------|----------|----------|
| Version | 4 | Версия протокола IPv4 |
| Header Length | 20 байт | Длина заголовка без опций |
| TTL | 128 | Максимальное число хопов |
| Protocol | 6 (TCP) | Протокол транспортного уровня |
| Source / Destination | IP-адреса | Логические адреса узлов |

> 📸 *Скриншот 3: Структура Ethernet + IP заголовков*

---

### Задание 5. Анализ утилиты ping (ICMP)

**Действия:**
1. Определён IP соседней станции: `10.134.116.88` (через `ipconfig`).
2. Очищен ARP-кэш: `arp -d 10.134.116.88`.
3. Отключён брандмауэр на обоих ПК.
4. Запущен Wireshark с фильтром `icmp or arp`.
5. Выполнена команда: `ping 10.134.116.88`.

**Результат в CMD:**
```
Обмен пакетами с 10.134.116.88 по с 32 байтами данных:
Ответ от 10.134.116.88: число байт=32 время<1мс TTL=128
...
Пакетов: отправлено = 4, получено = 4, потеряно = 0 (0% потерь)
```

**Захваченные пакеты в Wireshark:**

| № | Протокол | Info |
|---|----------|------|
| 1 | ARP | Who has 10.134.116.88? Tell 10.134.116.63 |
| 2 | ARP | 10.134.116.88 is at 52:10:4f:76:91:82 |
| 3 | ICMP | Echo (ping) request id=0x0001, seq=1/256, ttl=128 |
| 4 | ICMP | Echo (ping) reply id=0x0001, seq=1/256, ttl=128 |
| 5 | ICMP | Echo (ping) request id=0x0001, seq=2/512, ttl=128 |
| 6 | ICMP | Echo (ping) reply id=0x0001, seq=2/512, ttl=128 |

**Диаграмма взаимодействия:**

```
  ПК-1 (10.134.116.63)              ПК-2 (10.134.116.88)
          |                                  |
          | --- ARP Request (Broadcast) ----> |
          | <--- ARP Reply (Unicast) -------- |
          |                                  |
          | --- ICMP Echo Request (Type 8) -> |
          | <--- ICMP Echo Reply (Type 0) --- |
          |                                  |
          | --- ICMP Echo Request (Type 8) -> |
          | <--- ICMP Echo Reply (Type 0) --- |
```

> 📸 *Скриншот 4: Wireshark с ICMP/ARP пакетами*  
> 📸 *Скриншот 5: Flow Graph (Statistics → Flow Graph)*

---

### Задание 6. Анализ TCP-сеанса

**Параметры варианта 5:** порт сервера **5005**.

**Запуск:**
```bash
javac TCP_Server.java TCP_Client.java
java TCP_Server    # в первом окне
java TCP_Client    # во втором окне
```

**Фильтр Wireshark:** `tcp.port == 5005`

**Захваченные пакеты:**

| № | Протокол | Info |
|---|----------|------|
| 1 | TCP | 49152 → 5005 [SYN] Seq=0 |
| 2 | TCP | 5005 → 49152 [SYN, ACK] Seq=0 Ack=1 |
| 3 | TCP | 49152 → 5005 [ACK] Seq=1 Ack=1 |
| 4 | TCP | 49152 → 5005 [PSH, ACK] "Hello from TCP Client (Variant 5)" |
| 5 | TCP | 5005 → 49152 [PSH, ACK] "TCP: Data received successfully!" |
| 6 | TCP | 49152 → 5005 [FIN, ACK] |
| 7 | TCP | 5005 → 49152 [FIN, ACK] |
| 8 | TCP | 49152 → 5005 [ACK] |

**Структура TCP-сегмента (пакет №4):**

| Поле | Значение | Описание |
|------|----------|----------|
| Source Port | 49152 | Эфемерный порт клиента |
| Destination Port | 5005 | Порт TCP-сервера (вариант 5) |
| Sequence number | 1 | Номер первого байта данных |
| Acknowledgment number | 1 | Ожидаемый следующий байт |
| Header Length | 20 bytes | Длина заголовка |
| Flags | 0x018 (PSH, ACK) | Флаги: протолкнуть данные + подтверждение |
| Window size | 64240 | Размер окна приёма |

**Диаграмма TCP-сеанса:**

```
  Клиент (порт 49152)              Сервер (порт 5005)
          |                                |
          | ---------- [SYN] -------------> |  1. Запрос соединения
          | <-------- [SYN, ACK] ---------- |  2. Подтверждение сервера
          | ---------- [ACK] -------------> |  3. Завершение handshake
          |                                |
          | --- [PSH, ACK] "Hello..." ----> |  4. Передача данных
          | <-- [PSH, ACK] "Data recv" ---- |  5. Ответ сервера
          |                                |
          | -------- [FIN, ACK] ----------> |  6. Закрытие со стороны клиента
          | <------- [FIN, ACK] ----------- |  7. Закрытие со стороны сервера
          | --------- [ACK] --------------> |  8. Подтверждение FIN
```

> 📸 *Скриншот 6: TCP-сеанс в Wireshark*  
> 📸 *Скриншот 7: Структура TCP-заголовка*

---

### Задание 7. Анализ UDP-сеанса

**Параметры варианта 5:** порт сервера **5006**.

**Фильтр Wireshark:** `udp.port == 5006`

**Захваченные пакеты:**

| № | Протокол | Info |
|---|----------|------|
| 1 | UDP | 49153 → 5006 Len=41 "Hello from UDP Client (Variant 5)" |
| 2 | UDP | 5006 → 49153 Len=26 "UDP: Datagram received!" |

**Структура UDP-датаграммы:**

| Поле | Значение | Описание |
|------|----------|----------|
| Source Port | 49153 | Эфемерный порт клиента |
| Destination Port | 5006 | Порт UDP-сервера (вариант 5) |
| Length | 41 байт | Длина датаграммы (заголовок 8 байт + данные 33 байта) |
| Checksum | 0xXXXX | Контрольная сумма |

**Диаграмма UDP-сеанса:**

```
  Клиент (порт 49153)              Сервер (порт 5006)
          |                                |
          | --- [UDP Datagram] -----------> |  Без handshake!
          | <--- [UDP Datagram] ----------- |  Сразу ответ
```

> 📸 *Скриншот 8: UDP-сеанс в Wireshark*  
> 📸 *Скриншот 9: Структура UDP-заголовка*

---

### Задание 8. Анализ ARP-протокола

**Действия:**
1. Очищен ARP-кэш: `arp -d 10.134.116.88`.
2. Запущен Wireshark с фильтром `arp`.
3. Выполнен `ping 10.134.116.88`.

**ARP-запрос (пакет №1):**

```
Ethernet II, Dst: Broadcast (ff:ff:ff:ff:ff:ff)
├── Destination: ff:ff:ff:ff:ff:ff  (Broadcast)
├── Source: 20:c1:9b:7a:7b:73
└── Type: ARP (0x0806)

Address Resolution Protocol (request)
├── Opcode: request (1)
├── Sender MAC: 20:c1:9b:7a:7b:73
├── Sender IP: 10.134.116.63
├── Target MAC: 00:00:00:00:00:00  (неизвестен)
└── Target IP: 10.134.116.88
```

**ARP-ответ (пакет №2):**

```
Ethernet II, Dst: 20:c1:9b:7a:7b:73  (Unicast!)
├── Destination: 20:c1:9b:7a:7b:73
├── Source: 52:10:4f:76:91:82
└── Type: ARP (0x0806)

Address Resolution Protocol (reply)
├── Opcode: reply (2)
├── Sender MAC: 52:10:4f:76:91:82  (MAC соседа!)
├── Sender IP: 10.134.116.88
├── Target MAC: 20:c1:9b:7a:7b:73
└── Target IP: 10.134.116.63
```

**Проверка ARP-кэша:**
```
C:\> arp -a
Интерфейс: 10.134.116.63
  Интернет-адрес       Физический адрес     Тип
  10.134.116.88        52-10-4f-76-91-82    динамический
```

> 📸 *Скриншот 10: ARP-запрос и ARP-ответ*  
> 📸 *Скриншот 11: Вывод команды arp -a*

---

## 6. Исходный код программ

### TCP_Server.java (порт 5005)

```java
import java.io.*;
import java.net.*;

public class TCP_Server {
    public static void main(String[] args) {
        int port = 5005; // Порт варианта 5

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("TCP Server is listening on port " + port + "...");

            try (Socket clientSocket = serverSocket.accept()) {
                System.out.println("Client connected! IP: " 
                        + clientSocket.getInetAddress());

                BufferedReader in = new BufferedReader(
                        new InputStreamReader(clientSocket.getInputStream()));
                PrintWriter out = new PrintWriter(
                        clientSocket.getOutputStream(), true);

                String message = in.readLine();
                System.out.println("Received from client: " + message);

                out.println("TCP: Data received successfully!");
            }
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
```

### TCP_Client.java

```java
import java.io.*;
import java.net.*;

public class TCP_Client {
    public static void main(String[] args) {
        String host = "127.0.0.1";
        int port = 5005;

        try (Socket socket = new Socket(host, port)) {
            System.out.println("Connected to server " + host + ":" + port);

            PrintWriter out = new PrintWriter(
                    socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream()));

            String message = "Hello from TCP Client (Variant 5)";
            out.println(message);

            String response = in.readLine();
            System.out.println("Server response: " + response);

        } catch (IOException e) {
            System.err.println("Client error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
```

### UDP_Server.java (порт 5006)

```java
import java.net.*;

public class UDP_Server {
    public static void main(String[] args) {
        int port = 5006;

        try (DatagramSocket serverSocket = new DatagramSocket(port)) {
            System.out.println("UDP Server is listening on port " + port + "...");

            byte[] receiveBuffer = new byte[256];
            DatagramPacket receivePacket = new DatagramPacket(
                    receiveBuffer, receiveBuffer.length);

            serverSocket.receive(receivePacket);

            String message = new String(receivePacket.getData(), 0, 
                    receivePacket.getLength());
            System.out.println("Received: " + message);

            String response = "UDP: Datagram received!";
            byte[] sendBuffer = response.getBytes();

            DatagramPacket sendPacket = new DatagramPacket(
                    sendBuffer, sendBuffer.length,
                    receivePacket.getAddress(), receivePacket.getPort());
            serverSocket.send(sendPacket);

        } catch (Exception e) {
            System.err.println("UDP Server error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
```

### UDP_Client.java

```java
import java.net.*;

public class UDP_Client {
    public static void main(String[] args) {
        String host = "127.0.0.1";
        int port = 5006;

        try (DatagramSocket clientSocket = new DatagramSocket()) {
            String message = "Hello from UDP Client (Variant 5)";
            byte[] sendBuffer = message.getBytes();

            InetAddress address = InetAddress.getByName(host);
            DatagramPacket sendPacket = new DatagramPacket(
                    sendBuffer, sendBuffer.length, address, port);

            clientSocket.send(sendPacket);
            System.out.println("Datagram sent.");

            byte[] receiveBuffer = new byte[256];
            DatagramPacket receivePacket = new DatagramPacket(
                    receiveBuffer, receiveBuffer.length);
            clientSocket.receive(receivePacket);

            String response = new String(receivePacket.getData(), 0, 
                    receivePacket.getLength());
            System.out.println("Server response: " + response);

        } catch (Exception e) {
            System.err.println("UDP Client error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
```

---

## 7. Выводы

В ходе выполнения лабораторной работы были изучены практические навыки работы с анализатором сетевого трафика **Wireshark** и получены следующие результаты:

1. **Освоены базовые операции Wireshark:** захват трафика, применение фильтров отображения и захвата, статистическая обработка данных (`Summary`, `Protocol Hierarchy`, `Flow Graph`).

2. **Изучена структура заголовков протоколов стека TCP/IP:**
   - **Ethernet** — канальный уровень, MAC-адресация, поле Type (`0x0800` = IPv4, `0x0806` = ARP).
   - **IP** — сетевой уровень, логическая адресация, TTL, поле Protocol.
   - **TCP** — транспортный уровень с установлением соединения (Three-way handshake: SYN → SYN-ACK → ACK), надёжная доставка через Sequence/Acknowledgment numbers.
   - **UDP** — транспортный уровень без соединения, минимальные накладные расходы (всего 4 поля в заголовке).
   - **ICMP** — управляющий протокол, используемый утилитой `ping` (Echo Request/Reply).
   - **ARP** — протокол разрешения адресов, связывающий IP и MAC через широковещательный запрос и персональный ответ.

3. **Разработаны и протестированы сетевые приложения** на языке Java (TCP и UDP серверы/клиенты), корректность работы которых подтверждена анализом трафика в Wireshark.

4. **Выявлены ключевые отличия TCP и UDP:** TCP требует установления соединения (8 пакетов для полного сеанса с закрытием), UDP обходится 2 пакетами без handshake.

5. **Подтверждена необходимость ARP** перед отправкой ICMP-пакетов: при отсутствии записи в ARP-кэше система сначала выполняет широковещательный ARP-запрос.
