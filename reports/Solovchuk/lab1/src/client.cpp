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
    log_event("=== Client Started ===");
    printf("TCP DEMO CLIENT (Variant 4)\n");
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
