#include <winsock2.h>
#include <ws2tcpip.h>
#include <windows.h>
#include <stdio.h>
#include <string.h>
#include <time.h>

#pragma comment(lib, "ws2_32.lib")
#define SERVER_PORT 666
#define SERVER_ADDR "127.0.0.1"
#define LOG_FILE "client_tcp_log.txt"

void LogEvent(const char* event, const char* message) {
    time_t now = time(NULL);
    struct tm* tm_info = localtime(&now);
    char time_str[32];
    strftime(time_str, sizeof(time_str), "%Y-%m-%d %H:%M:%S", tm_info);

    FILE* log = fopen(LOG_FILE, "a");
    if (log) {
        fprintf(log, "[%s] %s: %s\n", time_str, event, message);
        fclose(log);
    }
}

int main() {
    WSADATA wsaData;
    if (WSAStartup(MAKEWORD(2, 2), &wsaData) != 0) {
        printf("Ошибка WSAStartup: %d\n", WSAGetLastError());
        return -1;
    }

    SOCKET sock = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP);
    if (sock == INVALID_SOCKET) {
        printf("Ошибка сокета: %d\n", WSAGetLastError());
        WSACleanup();
        return -1;
    }

    sockaddr_in server_addr;
    server_addr.sin_family = AF_INET;
    server_addr.sin_port = htons(SERVER_PORT);
    inet_pton(AF_INET, SERVER_ADDR, &server_addr.sin_addr);

    if (connect(sock, (sockaddr*)&server_addr, sizeof(server_addr)) == SOCKET_ERROR) {
        printf("Ошибка подключения: %d\n", WSAGetLastError());
        closesocket(sock);
        WSACleanup();
        return -1;
    }
    printf("Подключено к %s:%d\n", SERVER_ADDR, SERVER_PORT);
    LogEvent("CONNECT", "Соединение установлено");

    char buffer[8192];
    while (1) {
        printf("> ");
        if (!fgets(buffer, sizeof(buffer), stdin)) break;

        size_t len = strlen(buffer);
        if (len > 0 && buffer[len - 1] == '\n') buffer[len - 1] = '\0';

        if (strncmp(buffer, "disconnect", 10) == 0) {
            LogEvent("SEND", buffer);
            send(sock, buffer, (int)strlen(buffer), 0);
            break;
        }

        LogEvent("SEND", buffer);
        if (send(sock, buffer, (int)strlen(buffer), 0) == SOCKET_ERROR) {
            printf("Ошибка отправки: %d\n", WSAGetLastError());
            break;
        }

        int bytes_received = recv(sock, buffer, sizeof(buffer) - 1, 0);
        
        if (bytes_received > 0) {
            buffer[bytes_received] = '\0';
            printf("\n[Ответ сервера]\n%s\n", buffer);
            LogEvent("RECV", buffer);
        } 
        else if (bytes_received == 0) {
            printf("Сервер закрыл соединение.\n");
            break;
        } 
        else {
            int err = WSAGetLastError();
            if (err == 10053) {
                printf("Соединение было разорвано сервером (Ошибка 10053).\n");
            } else {
                printf("Ошибка приема: %d\n", err);
            }
            break;
        }
    }

    LogEvent("DISCONNECT", "Соединение разорвано");
    closesocket(sock);
    WSACleanup();
    return 0;
}