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