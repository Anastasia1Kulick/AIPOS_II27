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
    ├── server.cpp
    ├── server.exe
    ├── client.cpp
    ├── client.exe
    ├── s.txt (файл для считывания строк)
    └── server_log.txt (создаётся автоматически)
```
# Код программы
## Клиентская часть

```c++
#define _WINSOCK_DEPRECATED_NO_WARNINGS   
#define _CRT_SECURE_NO_WARNINGS
#include <stdio.h>
#include <iostream>
#include <string.h>
#include <conio.h>         
#include <winsock2.h>
#include <windows.h>
#include <time.h>

#pragma comment(lib, "ws2_32.lib")

#define PORT 669
#define SERVERADDR "127.0.0.1"
#define LOG_FILE "client_log.txt"

// Записи события в лог-файл 
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
    char buff[1024];
    char input_buffer[1024] = { 0 };
    int  input_len = 0;

    // Инициализация лога
    log_event("Client Started");
    printf("TCP CLIENT (Variant 4)\n");
    printf("Press PgDn to send the typed line.\n\n");

    // Инициализация Winsock 
    WSADATA wsaData;
    if (WSAStartup(0x202, &wsaData)) {
        printf("WSAStartup error %d\n", WSAGetLastError());
        log_event("WSAStartup failed");
        return -1;
    }

    // Создание сокета 
    SOCKET my_sock = socket(AF_INET, SOCK_STREAM, 0);
    if (my_sock == INVALID_SOCKET) {
        printf("Socket() error %d\n", WSAGetLastError());
        log_event("Socket creation failed");
        WSACleanup();
        return -1;
    }

    // Автоматическое подключение к серверу 
    sockaddr_in dest_addr;
    dest_addr.sin_family = AF_INET;
    dest_addr.sin_port = htons(PORT);

    if (inet_addr(SERVERADDR) != INADDR_NONE) {
        dest_addr.sin_addr.s_addr = inet_addr(SERVERADDR);
    }
    else {
        HOSTENT* hst = gethostbyname(SERVERADDR);
        if (hst) {
            dest_addr.sin_addr.s_addr = ((unsigned long**)hst->h_addr_list)[0][0];
        }
        else {
            printf("Invalid address %s\n", SERVERADDR);
            log_event("Invalid address");
            closesocket(my_sock);
            WSACleanup();
            return -1;
        }
    }

    if (connect(my_sock, (sockaddr*)&dest_addr, sizeof(dest_addr))) {
        printf("Connect error %d\n", WSAGetLastError());
        log_event("Connection failed");
        closesocket(my_sock);
        WSACleanup();
        return -1;
    }

    // Логируем 
    {
        char msg[256];
        sprintf(msg, "Connected to %s:%d", SERVERADDR, PORT);
        log_event(msg);
    }
    printf("Connected to %s:%d\n", SERVERADDR, PORT);

    // Цикл обмена сообщениями 
    while (1) {
        // Проверка приёма данных от сервера 
        fd_set readfds;
        struct timeval tv;
        tv.tv_sec = 0;
        tv.tv_usec = 100000; 

        FD_ZERO(&readfds);
        FD_SET(my_sock, &readfds);

        int activity = select(0, &readfds, NULL, NULL, &tv);

        if (activity > 0) {
            int nsize = recv(my_sock, buff, sizeof(buff) - 1, 0);
            if (nsize > 0) {
                buff[nsize] = 0;
                printf("S=>C: %s", buff);

                // Логируем 
                char log_msg[2048];
                sprintf(log_msg, "Received: %s", buff);
                log_event(log_msg);
            }
            else if (nsize == 0) {
                printf("\nServer closed connection.\n");
                log_event("Server closed connection");
                break;
            }
            else {
                printf("Recv error %d\n", WSAGetLastError());
                log_event("Recv error");
                break;
            }
        }


        // Ввод
        if (_kbhit()) {
            int ch = _getch();

            // Клвиши
            if (ch == 0 || ch == 224) {
                ch = _getch(); 
                if (ch == 81) { //PgDn
                    if (input_len > 0) {
                        input_buffer[input_len] = 0;


                        // Отправляем 
                        send(my_sock, input_buffer, strlen(input_buffer), 0);

                        // Логируем 
                        char log_msg[2048];
                        sprintf(log_msg, "Sent: %s", input_buffer);
                        log_event(log_msg);

                        printf("\nC=>S: %s\n", input_buffer);

                        // Чистим буфер
                        input_len = 0;
                        memset(input_buffer, 0, sizeof(input_buffer));
                    }
                }
            }
            // Backspace
            else if (ch == 8) {
                if (input_len > 0) {
                    input_len--;
                    input_buffer[input_len] = 0;
                    printf("\b \b");
                }
            }
            // Обычный символ
            else if (ch >= 32 && ch <= 126) {
                if (input_len < sizeof(input_buffer) - 1) {
                    input_buffer[input_len++] = ch;
                    printf("%c", ch);
                }
            }
        }
    }

    log_event("=== Client Stopped ===");
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
#include <winsock2.h>
#include <windows.h>

#pragma comment(lib, "ws2_32.lib")

#define MY_PORT 669
#define BUFF_SIZE 1024

DWORD WINAPI WorkWithClient(LPVOID client_socket);

int nclients = 0; 

int main(int argc, char* argv[]) {
    setlocale(LC_ALL, "rus");
    char buff[BUFF_SIZE];
    printf("TCP SERVER (Variant 9: load <filename>)\n");

    // Инициализация Winsock
    WSADATA wsaData;
    if (WSAStartup(0x0202, &wsaData)) {
        printf("Error WSAStartup %d\n", WSAGetLastError());
        return -1;
    }

    // Создание сокета
    SOCKET mysocket = socket(AF_INET, SOCK_STREAM, 0);
    if (mysocket == INVALID_SOCKET) {
        printf("Error socket %d\n", WSAGetLastError());
        WSACleanup();
        return -1;
    }

    // Связывание сокета с локальным адресом
    sockaddr_in local_addr;
    local_addr.sin_family = AF_INET;
    local_addr.sin_port = htons(MY_PORT);
    local_addr.sin_addr.s_addr = INADDR_ANY; // Принимаем на все IP

    if (bind(mysocket, (sockaddr*)&local_addr, sizeof(local_addr))) {
        printf("Error bind %d\n", WSAGetLastError());
        closesocket(mysocket);
        WSACleanup();
        return -1;
    }

    // Ожидание подключений
    if (listen(mysocket, 0x100)) {
        printf("Error listen %d\n", WSAGetLastError());
        closesocket(mysocket);
        WSACleanup();
        return -1;
    }
    printf("Ожидание подключений...\n");

    // Извлечение сообщений из очереди
    SOCKET client_socket;
    sockaddr_in client_addr;
    int client_addr_size = sizeof(client_addr);

    while ((client_socket = accept(mysocket, (sockaddr*)&client_addr, &client_addr_size))) {
        nclients++;

        // Получение имени хоста (для логов)
        HOSTENT* hst = gethostbyaddr((char*)&client_addr.sin_addr.s_addr, 4, AF_INET);
        printf("+%s [%s] new connect!\n", (hst) ? hst->h_name : "", inet_ntoa(client_addr.sin_addr));

        // Создание потока для клиента
        DWORD thID;
        CreateThread(NULL, NULL, WorkWithClient, &client_socket, NULL, &thID);
    }

    closesocket(mysocket);
    WSACleanup();
    return 0;
}

// Функция обслуживания клиента
DWORD WINAPI WorkWithClient(LPVOID client_socket) {
    SOCKET my_sock = ((SOCKET*)client_socket)[0];
    char buff[BUFF_SIZE];
    char filename[256];
    FILE* file;

    // Приветствие
    const char* hello = "Hello, Student! Enter command: load <filename>\r\n";
    send(my_sock, hello, strlen(hello), 0);

    int bytes_recv;
    while ((bytes_recv = recv(my_sock, buff, sizeof(buff) - 1, 0)) > 0) {
        buff[bytes_recv] = 0;
        buff[strcspn(buff, "\r\n")] = 0;

        printf("Client: %s\n", buff);

        if (strncmp(buff, "load ", 5) == 0) {
            strcpy(filename, buff + 5);

            file = fopen(filename, "r");
            if (file) {
                printf("Sending file: %s\n", filename);
                while (fgets(buff, sizeof(buff), file) != NULL) {
                    send(my_sock, buff, strlen(buff), 0);
                }
                fclose(file);
                send(my_sock, "\r\n[EOF]\r\n", 9, 0);
            }
            else {
                
                const char* err_msg = "Error: File not found. Closing connection.\r\n";
                send(my_sock, err_msg, strlen(err_msg), 0);
                printf("File not found. Closing connection.\n");
                break;
            }
        }
        else {
            const char* msg = "Unknown command. Use: load <filename>\r\n";
            send(my_sock, msg, strlen(msg), 0);
        }
    }

    nclients--;
    printf("--disconnect\n");
    closesocket(my_sock);
    return 0;
}
```
