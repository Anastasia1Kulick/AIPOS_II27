<p align="center">Министерство образования Республики Беларусь</p>
<p align="center">Учреждение образования</p>
<p align="center">“Брестский Государственный технический университет”</p>
<p align="center">Кафедра ИИТ</p>
<br><br><br><br><br><br><br>
<p align="center">Лабораторная работа №3</p>
<p align="center">По дисциплине “Аппаратное и Программное обеспечение сетей”</p>
<p align="center">Тема: “АНАЛИЗ СЕТЕВОГО ТРАФИКА И ПРОТОКОЛОВ НА БАЗЕ WIRESHARK”</p>
<p align="center">Вариант: 7</p>
<br><br><br><br><br>
<p align="right">Выполнил:</p>
<p align="right">Студент 3 курса</p>
<p align="right">Группы ИИ-27</p>
<p align="right">Дорошенко М.Д.</p>
<p align="right">Проверил:</p>
<p align="right">Кулик А.Д.</p>
<br><br><br><br><br>
<p align="center">Брест 2026</p>

---

# Цель

Ознакомиться с принципами работы анализатора трафика Wireshark, разборать структуры основных сетевых протоколов.

# Ход работы

Для начала установим программу [Wireshark](https://www.wireshark.org) на нашу машину.
После небольшого ознакомления с методическикми указаниями, перейдем к выполнению лабораторной работы.

## Задание 1

![task_1_1](./img/task_1_1.png)

![task_1_2](./img/task_1_2.png)

![task_1_3](./img/task_1_3.png)

---

## Задание 2

Статистические данные размеров IPv4-пакетов:

![task_2_1](./img/task_2_1.png)

![task_2_2](./img/task_2_2.png)

Рассмотрим один из поступиших к нам пакетов:

![task_2_3](./img/task_2_3.png)

Для него можнно выделить следующие вещи:

**Заголовок Ethernet II**:

- `Destination`: 60:f1:8a:47:89:9c
- `Source`: 10:6f:d9:51:bc:59
- `Type`: 0x0800

**Заголовок IPv4**:

- `Version`: 4
- `Header Length`: 20
- `Total Length`: 93
- `Identification`: 0xc645
- `Flags`: 0x02
- `Time to Live`: 65
- `Protocol`: TCP (6)
- `Header checksum`: 0xd87e
- `Source Address`: 192.168.100.30
- `Destination Address`: 93.125.24.147

---

## Задание 3

![task_3_1](./img/task_3_1.png)

Структура расмматриваемого пакета:

![task_3_2](./img/task_3_2.png)

**Заголовок Ethernet II**:

- `Destination`: 60:f1:8a:47:89:9c
- `Source`: 10:6f:d9:51:bc:59
- `Type`: 0x0800

**Заголовок IPv4**:

- `Version `: 4
- `Header Length`: 20
- `Total Length`: 60
- `Identification`: 0x4e28
- `Flags`: 0x00
- `Time to Live`: 65
- `Protocol`: ICMP (1)
- `Header checksum`: 0x0a72
- `Source Address`: 192.168.100.30
- `Destination Address`: 142.250.109.102

**Заголовок ICMP**:

- `Type`: Echo ping request (8)
- `Code`: 0
- `Checksum`: 0x4d5a
- `Identifier (BE)`: 1 (0x0001)
- `Identifier (LE)`: 256 (0x0100)
- `Sequence Number (BE)`: 1 (0x0001)
- `Sequence Number (LE)`: 256 (0x0100)

**Диаграмма взаимодействия**:

![task_3_3](./img/task_3_3.png)

---

## Задание 4

![task_4_1](./img/task_4_1.png)

**Заголовок TCP**:

![task_4_2](./img/task_4_2.png)

- `Source Port`: 8080
- `Destination Port`: 50589
- `Sequence Number`: 23    (relative sequence number)
- `Acknowledgment Number`: 94    (relative ack number)
- `Flags`: 0x011 (FIN, ACK)
- `Header Length`: 20 bytes (5)

**Диаграмма взаимодействия**:

![task_4_3](./img/task_4_3.png)

---

## Задание 5

![task_5_1](./img/task_5_1.png)

**Заголовок UDP**:

![task_5_2](./img/task_5_2.png)

- `Source Port`: 64985
- `Destination Port`: 666
- `Length`: 72
- `Checksum`: 0xb49c [unverified]

**Диаграмма взаимодействия**:

![task_5_3](./img/task_5_3.png)

---

## Задание 6

![task_6_1](./img/task_6_1.png)

**Заголовок ADR**:

![task_6_2](./img/task_6_2.png)

- `Opcode`: reply (2)
- `Sender MAC address`: f2:4b:cf:f3:6b:b9 (f2:4b:cf:f3:6b:b9)
- `Sender IP address`: 192.168.100.60
- `Target MAC address`: CloudNetwork_51:bc:59 (10:6f:d9:51:bc:59)
- `Target IP address`: 192.168.100.30

---

# Выводы

В ходе работы были изучены базовые возможности программы Wireshark. Освоены методы захвата трафика, применения фильтров отображения и анализа структуры сетевых протоколов (Ethernet, IP, TCP, UDP, ICMP, ARP).
