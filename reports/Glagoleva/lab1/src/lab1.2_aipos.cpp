#define _CRT_SECURE_NO_WARNINGS
#define _WINSOCK_DEPRECATED_NO_WARNINGS

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <winsock2.h>
#include <windows.h>

#pragma comment(lib, "ws2_32.lib")

#define DEFAULT_SERVER "127.0.0.1"
#define DEFAULT_PORT 666
#define LOG_FILE "protocol.log"

SOCKET my_sock = INVALID_SOCKET;
FILE* log_file = NULL;

// Функция для записи событий в файл протокола
void LogMessage(const char* format, ...) {
    if (!log_file) return;

    time_t rawtime;
    struct tm* timeinfo;
    char time_str[80];

    time(&rawtime);
    timeinfo = localtime(&rawtime);
    strftime(time_str, sizeof(time_str), "[%Y-%m-%d %H:%M:%S] ", timeinfo);

    fprintf(log_file, "%s", time_str);

    va_list args;
    va_start(args, format);
    vfprintf(log_file, format, args);
    va_end(args);

    fprintf(log_file, "\n");
    fflush(log_file); // Сбрасываем буфер, чтобы данные сразу писались на диск
}

// Функция подключения к серверу
int ConnectToServer(const char* ip, int port) {
    if (my_sock != INVALID_SOCKET) {
        printf("Already connected. Use 'disconnect' first.\n");
        return 0;
    }

    my_sock = socket(AF_INET, SOCK_STREAM, 0);
    if (my_sock == INVALID_SOCKET) {
        printf("Socket creation failed.\n");
        return 0;
    }

    sockaddr_in dest_addr;
    dest_addr.sin_family = AF_INET;
    dest_addr.sin_port = htons(port);
    dest_addr.sin_addr.s_addr = inet_addr(ip);

    if (connect(my_sock, (sockaddr*)&dest_addr, sizeof(dest_addr)) == SOCKET_ERROR) {
        printf("Connection failed to %s:%d\n", ip, port);
        closesocket(my_sock);
        my_sock = INVALID_SOCKET;
        return 0;
    }

    printf("Connected to %s:%d\n", ip, port);
    LogMessage("CONNECT to %s:%d - SUCCESS", ip, port);

    // Читаем приветствие от сервера
    char buff[1024];
    int bytes_recv = recv(my_sock, buff, sizeof(buff) - 1, 0);
    if (bytes_recv > 0) {
        buff[bytes_recv] = 0;
        printf("Server: %s", buff);
        LogMessage("RECV: %s", buff);
    }

    return 1;
}

// Функция отключения
void Disconnect() {
    if (my_sock != INVALID_SOCKET) {
        closesocket(my_sock);
        my_sock = INVALID_SOCKET;
        printf("Disconnected.\n");
        LogMessage("DISCONNECT");
    }
    else {
        printf("Not connected.\n");
    }
}

int main() {
    WSADATA wsaData;
    if (WSAStartup(0x0202, &wsaData)) {
        printf("WSAStartup failed.\n");
        return 1;
    }

    log_file = fopen(LOG_FILE, "a");
    if (!log_file) {
        printf("Cannot open log file.\n");
    }

    LogMessage("=== CLIENT STARTED ===");

    // Автоматическое подключение при запуске
    if (!ConnectToServer(DEFAULT_SERVER, DEFAULT_PORT)) {
        printf("Auto-connect failed. Use 'connect <ip> <port>'\n");
    }

    char buff[1024];
    printf("Commands: connect <ip> <port> | disconnect | quit\n");

    while (1) {
        // Если сокет не открыт, ждем команд
        if (my_sock == INVALID_SOCKET) {
            printf("Offline> ");
            if (!fgets(buff, sizeof(buff), stdin)) break;

            buff[strcspn(buff, "\n")] = 0;

            if (strncmp(buff, "connect ", 8) == 0) {
                char ip[32];
                int port;
                if (sscanf(buff + 8, "%s %d", ip, &port) == 2) {
                    ConnectToServer(ip, port);
                }
                else {
                    printf("Usage: connect <ip> <port>\n");
                }
            }
            else if (strcmp(buff, "quit") == 0) {
                break;
            }
            else {
                printf("Unknown command.\n");
            }
            continue;
        }

        // Если подключены - работаем
        printf("S<=C: ");
        if (!fgets(buff, sizeof(buff), stdin)) break;

        buff[strcspn(buff, "\n")] = 0;

        if (strcmp(buff, "disconnect") == 0) {
            Disconnect();
            continue;
        }
        if (strcmp(buff, "quit") == 0) {
            break;
        }

        if (strncmp(buff, "connect ", 8) == 0) {
            printf("Already connected. Disconnect first.\n");
            continue;
        }

        // Отправка данных на сервер
        int len = (int)strlen(buff);
        // Добавляем \r\n для корректной работы сервера
        char send_buf[1100];
        snprintf(send_buf, sizeof(send_buf), "%s\r\n", buff);

        if (send(my_sock, send_buf, (int)strlen(send_buf), 0) == SOCKET_ERROR) {
            printf("Send failed.\n");
            Disconnect();
            continue;
        }
        LogMessage("SENT: %s", buff);

        // Прием ответа
        int bytes_recv = recv(my_sock, buff, sizeof(buff) - 1, 0);

        if (bytes_recv > 0) {
            buff[bytes_recv] = 0;
            printf("S=>C: %s\n", buff);
            LogMessage("RECV: %s", buff);
        }
        else if (bytes_recv == 0) {
            printf("Server closed connection.\n");
            Disconnect();
        }
        else {
            printf("Recv error.\n");
            Disconnect();
        }
    }

    Disconnect();
    LogMessage("=== CLIENT FINISHED ===");

    if (log_file) fclose(log_file);
    WSACleanup();
    return 0;
}