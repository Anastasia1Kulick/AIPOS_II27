<p align="center">Министерство образования Республики Беларусь</p>
<p align="center">Учреждение образования</p>
<p align="center">“Брестский Государственный технический университет”</p>
<p align="center">Кафедра ИИТ</p>
<br><br><br><br><br><br><br>
<p align="center">Лабораторная работа №1</p>
<p align="center">По дисциплине “Аппаратное и Программное обеспечение сетей”</p>
<p align="center">Тема: “Организация TCP – сервера/клиента”</p>
<p align="center">Вариант: 9</p>
<br><br><br><br><br>
<p align="right">Выполнил:</p>
<p align="right">Студент 3 курса</p>
<p align="right">Группы ИИ-27</p>
<p align="right">Соловчук И.Г..</p>
<p align="right">Проверила:</p>
<p align="right">Кулик А.Д.</p>
<br><br><br><br><br>
<p align="center">Брест 2026</p>

# Цель
Изучить основы программирования сетевых приложений на базе библиотеки java.net;
Приобрести навыки по практическому использованию библиотеки для реализации сетевых приложений на базе протоколов TCP и UDP.

# Задание на выполнение
```Изучить теоретический материал, функции Winsock и листинг программы реализации TCP-сервера. Получить индивидуальное задание у преподавателя.```

```Разработать программу работы TCP-эхо-сервера, выполняющую функции согласноварианта задания (см. приложения). В качестве клиента использовать программу telnet.Выполнить проверку программы согласно методике, приведенной в разделе 7.1.Продемонстрировать работу системы преподавателю.```

```Представить отчет, содержащий титульный лист, листинг программы с подробными комментариями основных фрагментов программы.8.4 Подготовиться к защите лабораторной работы по теоретическому материалу, функциям,собственным результатам, полученным в ходе выполнения лабораторной работы```

# Задания для реализации TCP сервера
Отсылка клиенту содержимого текстового файла <имя файла> в случае приема сервером в потоке символов команды load fname.txt, <имя файла> – имя некоторого текстового файла, находящегося в каталоге сервера. В случае, если запрашиваемый файл отсутствует в каталоге сервера, сервер должен отослать сообщение об этом и разорвать соединение.

# Задания для реализации TCP клиента
Ввод символов с отсылкой введенной строки по нажатию на клавишу PgDn. 
Ведение файла протокола событий, включающих: 
    1) время начала и окончания соединения;
    2) передаваемую серверу строку и время передачи строки;
    3) принимаемую от  сервера строку и время  приема строки
Автоматическое подключение к серверу с заданным по умолчанию адресом при запуске клиента


# Структура программы
```
reports/Solovchuk/lab1/
├── rep/
│   └── README.md
└── src/
    ├── UDP-client.cpp
    ├── UDP-server.cpp
    ├── s.txt (файл для считывания строк)
    └── client_log.txt (создаётся автоматически)
```
# Код программы
## Клиентская часть

```c++
#define _WINSOCK_DEPRECATED_NO_WARNINGS
#define _CRT_SECURE_NO_WARNINGS


#include <stdio.h>
#include <iostream>
#include <stdlib.h>
#include <string.h>
#include <conio.h>
#include <time.h>
#include <winsock2.h>
#include <windows.h>

#pragma comment(lib, "ws2_32.lib")

#define PORT       670
#define SERVERADDR "127.0.0.1"
#define LOG_FILE   "client_log.txt"

void log_event(const char* event) {
    FILE* log = fopen(LOG_FILE, "a");
    if (log) {
        time_t rawtime;
        struct tm* timeinfo;
        char timebuf[80];
        time(&rawtime);
        timeinfo = localtime(&rawtime);
        strftime(timebuf, sizeof(timebuf), "%Y-%m-%d %H:%M:%S", timeinfo);
        fprintf(log, "[%s] %s\n", timebuf, event);
        fclose(log);
    }
}

int main(int argc, char* argv[]) {
    setlocale(LC_ALL, "rus");
    char buff[10 * 1024];
    printf("UDP DEMO CLIENT (Variant 4)\n");
    printf("Press PgDn to send the typed line.\n\n");

    log_event("Client Started");

    //Winsock
    WSADATA wsaData;
    if (WSAStartup(0x202, &wsaData)) {
        printf("WSAStartup error %d\n", WSAGetLastError());
        return -1;
    }

    //сокет
    SOCKET my_sock = socket(AF_INET, SOCK_DGRAM, 0);
    if (my_sock == INVALID_SOCKET) {
        printf("Socket() error %d\n", WSAGetLastError());
        WSACleanup();
        return -1;
    }

    //адрес сервера
    sockaddr_in dest_addr;
    dest_addr.sin_family = AF_INET;
    dest_addr.sin_port = htons(PORT);

    if (inet_addr(SERVERADDR) != INADDR_NONE) {
        dest_addr.sin_addr.s_addr = inet_addr(SERVERADDR);
    }
    else {
        HOSTENT* hst = gethostbyname(SERVERADDR);
        if (hst) {
            dest_addr.sin_addr.s_addr =
                ((unsigned long**)hst->h_addr_list)[0][0];
        }
        else {
            printf("Invalid address %s\n", SERVERADDR);
            closesocket(my_sock);
            WSACleanup();
            return -1;
        }
    }

    {
        char msg[256];
        sprintf(msg, "Target server: %s:%d", SERVERADDR, PORT);
        log_event(msg);
    }
    printf("Target server: %s:%d\n", SERVERADDR, PORT);

    char input_buffer[1024] = { 0 };
    int  input_len = 0;

    while (1) {
        // Приём датаграмм
        fd_set readfds;
        struct timeval tv;
        tv.tv_sec = 0;
        tv.tv_usec = 100000;
        FD_ZERO(&readfds);
        FD_SET(my_sock, &readfds);

        if (select(0, &readfds, NULL, NULL, &tv) > 0) {
            sockaddr_in from_addr;
            int from_len = sizeof(from_addr);
            int n = recvfrom(my_sock, buff, sizeof(buff) - 1, 0,
                (sockaddr*)&from_addr, &from_len);
            if (n > 0) {
                buff[n] = 0;
                printf("S=>C: %s", buff);

                char log_msg[2048];
                sprintf(log_msg, "Received: %.500s", buff);
                log_event(log_msg);

                if (strcmp(buff, "[EOF]") == 0) {
                    printf("\n[File transfer completed]\n");
                    log_event("File transfer completed");
                }
            }
        }

        // Ввод с клавиатуры
        if (_kbhit()) {
            int ch = _getch();

            if (ch == 0 || ch == 224) {
                ch = _getch();
                if (ch == 81) {   // PgDn
                    if (input_len > 0) {
                        input_buffer[input_len] = 0;

                        log_event("User input:");
                        {
                            char log_msg[2048];
                            sprintf(log_msg, "User input: %s", input_buffer);
                            log_event(log_msg);
                        }

                        sendto(my_sock, input_buffer, strlen(input_buffer), 0,
                            (sockaddr*)&dest_addr, sizeof(dest_addr));

                        {
                            char log_msg[2048];
                            sprintf(log_msg, "Sent to server: %s", input_buffer);
                            log_event(log_msg);
                        }

                        printf("\nC=>S: %s\n", input_buffer);

                        input_len = 0;
                        memset(input_buffer, 0, sizeof(input_buffer));
                    }
                }
            }
            else if (ch == 8) {   // Backspace
                if (input_len > 0) {
                    input_len--;
                    input_buffer[input_len] = 0;
                    printf("\b \b");
                }
            }
            else if (ch >= 32 && ch <= 126) {
                if (input_len < sizeof(input_buffer) - 1) {
                    input_buffer[input_len++] = ch;
                    printf("%c", ch);
                }
            }
        }
    }

    log_event("Client Stopped");
    closesocket(my_sock);
    WSACleanup();
    return 0;
}
```
## Серверная часть
```c++
#define _WINSOCK_DEPRECATED_NO_WARNINGS
#define _CRT_SECURE_NO_WARNINGS

#include <stdio.h>
#include <iostream>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <winsock2.h>
#include <windows.h>

#pragma comment(lib, "ws2_32.lib")

#define PORT      670
#define BUFF_SIZE 1024
#define CHUNK     1000
#define LOG_FILE  "server_log.txt"

void log_event(const char* event) {
    FILE* log = fopen(LOG_FILE, "a");
    if (log) {
        time_t rawtime;
        struct tm* timeinfo;
        char timebuf[80];
        time(&rawtime);
        timeinfo = localtime(&rawtime);
        strftime(timebuf, sizeof(timebuf), "%Y-%m-%d %H:%M:%S", timeinfo);
        fprintf(log, "[%s] %s\n", timebuf, event);
        fclose(log);
    }
}

int main(int argc, char* argv[]) {
    setlocale(LC_ALL, "rus");
    char buff[BUFF_SIZE];
    printf("UDP SERVER (Variant 9: load <filename>)\n");

    log_event("Server Started");

    //Winsock
    WSADATA wsaData;
    if (WSAStartup(0x0202, &wsaData)) {
        printf("WSAStartup error %d\n", WSAGetLastError());
        return -1;
    }

    //сокет
    SOCKET my_sock = socket(AF_INET, SOCK_DGRAM, 0);
    if (my_sock == INVALID_SOCKET) {
        printf("Socket() error %d\n", WSAGetLastError());
        WSACleanup();
        return -1;
    }

    //bind
    sockaddr_in local_addr;
    local_addr.sin_family = AF_INET;
    local_addr.sin_addr.s_addr = INADDR_ANY;
    local_addr.sin_port = htons(PORT);

    if (bind(my_sock, (sockaddr*)&local_addr, sizeof(local_addr))) {
        printf("bind error %d\n", WSAGetLastError());
        closesocket(my_sock);
        WSACleanup();
        return -1;
    }

    printf("UDP-сервер запущен на порту %d. Ожидание датаграмм...\n", PORT);

    //цикл обработки
    while (1) {
        sockaddr_in client_addr;
        int client_addr_size = sizeof(client_addr);

        int bsize = recvfrom(my_sock, buff, sizeof(buff) - 1, 0,
            (sockaddr*)&client_addr, &client_addr_size);
        if (bsize == SOCKET_ERROR) {
            printf("recvfrom() error: %d\n", WSAGetLastError());
            continue;
        }
        buff[bsize] = 0;
        buff[strcspn(buff, "\r\n")] = 0;

        HOSTENT* hst = gethostbyaddr((char*)&client_addr.sin_addr, 4, AF_INET);
        printf("+%s [%s:%d] datagram: '%s'\n",
            hst ? hst->h_name : "Unknown",
            inet_ntoa(client_addr.sin_addr),
            ntohs(client_addr.sin_port),
            buff);

        char log_msg[2048];
        sprintf(log_msg, "Received from client: %s", buff);
        log_event(log_msg);

        // Обработка "load <filename>"
        if (strncmp(buff, "load ", 5) == 0) {
            char filename[256];
            strcpy(filename, buff + 5);

            FILE* file = fopen(filename, "rb");
            if (file) {
                printf("Sending file '%s'...\n", filename);
                log_event("File found, sending...");

                char chunk[CHUNK];
                int  read_bytes;
                while ((read_bytes = fread(chunk, 1, CHUNK, file)) > 0) {
                    sendto(my_sock, chunk, read_bytes, 0,
                        (sockaddr*)&client_addr, client_addr_size);
                    Sleep(1);
                }
                fclose(file);

                const char* eof = "[EOF]";
                sendto(my_sock, eof, strlen(eof), 0,
                    (sockaddr*)&client_addr, client_addr_size);
                log_event("Sent to client: [EOF]");
            }
            else {
                const char* err = "ERROR: File not found";
                sendto(my_sock, err, strlen(err), 0,
                    (sockaddr*)&client_addr, client_addr_size);
                log_event("File not found — error sent");
                printf("File '%s' not found.\n", filename);
            }
        }
        else {
            const char* msg = "Unknown command. Use: load <filename>";
            sendto(my_sock, msg, strlen(msg), 0,
                (sockaddr*)&client_addr, client_addr_size);
        }
    }

    log_event("Server Stopped");
    closesocket(my_sock);
    WSACleanup();
    return 0;
}
```