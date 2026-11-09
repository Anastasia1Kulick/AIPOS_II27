#include <winsock2.h>
#include <ws2tcpip.h>
#include <windows.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#pragma comment(lib, "ws2_32.lib")
#define SERVER_PORT 666

DWORD WINAPI ClientThread(LPVOID lpParam) {
    SOCKET client_sock = (SOCKET)lpParam;
    char buffer[8192];
    
    while (1) {
        int bytes_received = recv(client_sock, buffer, sizeof(buffer) - 1, 0);
        
        if (bytes_received <= 0) {
            break; 
        }
        
        buffer[bytes_received] = '\0';
        printf("Получен запрос: %s\n", buffer);

        if (strncmp(buffer, "disconnect", 10) == 0) {
            printf("Клиент запросил разрыв соединения.\n");
            break;
        }

        char command[16], filename[256];
        if (sscanf(buffer, "%15s %255s", command, filename) == 2 && strcmp(command, "load") == 0) {
            FILE* file = fopen(filename, "rb");
            if (file) {
                size_t bytes_read;
                while ((bytes_read = fread(buffer, 1, sizeof(buffer), file)) > 0) {
                    send(client_sock, buffer, (int)bytes_read, 0);
                }
                fclose(file);
                
                const char* end_marker = "\r\n[--- END OF FILE ---]\r\n";
                send(client_sock, end_marker, (int)strlen(end_marker), 0);
                
                printf("Файл '%s' отправлен.\n", filename);
            } else {
                const char* msg = "Ошибка: Файл не найден на сервере!\r\n";
                send(client_sock, msg, (int)strlen(msg), 0);
                printf("Файл не найден: %s\n", filename);
                break; 
            }
        } else {
            const char* msg = "Неверная команда. Ожидается: load <имя_файла>\r\n";
            send(client_sock, msg, (int)strlen(msg), 0);
        }
    }
    
    shutdown(client_sock, SD_BOTH);
    closesocket(client_sock);
    return 0;
}

int main() {
    WSADATA wsaData;
    if (WSAStartup(MAKEWORD(2, 2), &wsaData) != 0) {
        printf("Ошибка Winsock: %d\n", WSAGetLastError());
        return -1;
    }

    SOCKET server_sock = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP);
    if (server_sock == INVALID_SOCKET) {
        printf("Ошибка сокета: %d\n", WSAGetLastError());
        WSACleanup();
        return -1;
    }

    sockaddr_in server_addr;
    server_addr.sin_family = AF_INET;
    server_addr.sin_port = htons(SERVER_PORT);
    server_addr.sin_addr.s_addr = INADDR_ANY;

    if (bind(server_sock, (sockaddr*)&server_addr, sizeof(server_addr)) == SOCKET_ERROR) {
        printf("Ошибка bind: %d\n", WSAGetLastError());
        closesocket(server_sock);
        WSACleanup();
        return -1;
    }

    if (listen(server_sock, SOMAXCONN) == SOCKET_ERROR) {
        printf("Ошибка listen: %d\n", WSAGetLastError());
        closesocket(server_sock);
        WSACleanup();
        return -1;
    }

    printf("TCP Сервер запущен. Порт: %d.\n", SERVER_PORT);

    while (1) {
        sockaddr_in client_addr;
        int client_len = sizeof(client_addr);
        SOCKET client_sock = accept(server_sock, (sockaddr*)&client_addr, &client_len);
        
        if (client_sock != INVALID_SOCKET) {
            char client_ip[INET_ADDRSTRLEN];
            inet_ntop(AF_INET, &(client_addr.sin_addr), client_ip, INET_ADDRSTRLEN);
            printf("Клиент: %s:%d\n", client_ip, ntohs(client_addr.sin_port));

            DWORD thread_id;
            CreateThread(NULL, 0, ClientThread, (LPVOID)client_sock, 0, &thread_id);
        }
    }

    closesocket(server_sock);
    WSACleanup();
    return 0;
}