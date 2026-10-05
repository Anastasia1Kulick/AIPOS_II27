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