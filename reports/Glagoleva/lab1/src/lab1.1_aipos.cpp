#define _CRT_SECURE_NO_WARNINGS
#define _WINSOCK_DEPRECATED_NO_WARNINGS

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <winsock2.h>
#include <windows.h>

#pragma comment(lib, "ws2_32.lib")

#define MY_PORT 666
#define BUFF_SIZE 4096

void SendFileToClient(SOCKET client_sock, const char* filename) {
    FILE* f = fopen(filename, "rb");
    if (f == NULL) {
        const char* err_msg = "ERROR: File not found\r\n";
        send(client_sock, err_msg, (int)strlen(err_msg), 0);
        printf("[-] File %s not found. Disconnecting.\n", filename);
        return;
    }

    char buffer[BUFF_SIZE];
    int bytes_read;
    printf("[+] Sending file: %s\n", filename);
    while ((bytes_read = (int)fread(buffer, 1, BUFF_SIZE, f)) > 0) {
        send(client_sock, buffer, bytes_read, 0);
    }
    fclose(f);
    printf("[+] File sent successfully.\n");
}

DWORD WINAPI WorkWithClient(LPVOID client_socket) {
    SOCKET my_sock = ((SOCKET*)client_socket)[0];
    char buff[1024];

    const char* hello = "TCP SERVER (Variant 1). Enter command: load <filename>\r\n";
    send(my_sock, hello, (int)strlen(hello), 0);

    int bytes_recv;
    int total_recv = 0;

    while ((bytes_recv = recv(my_sock, buff + total_recv, sizeof(buff) - 1 - total_recv, 0)) > 0) {
        total_recv += bytes_recv;
        buff[total_recv] = 0;

        // ОТЛАДКА: Показываем, что именно пришло от Telnet
        printf("[DEBUG] Received %d bytes: '", bytes_recv);
        for (int i = 0; i < bytes_recv; i++) {
            if (buff[total_recv - bytes_recv + i] == '\r') printf("\\r");
            else if (buff[total_recv - bytes_recv + i] == '\n') printf("\\n");
            else printf("%c", buff[total_recv - bytes_recv + i]);
        }
        printf("'\n");

        char* newline = strchr(buff, '\n');
        if (newline == NULL) {
            continue; // Ждем Enter
        }

        *newline = 0;
        if (newline > buff && *(newline - 1) == '\r') {
            *(newline - 1) = 0;
        }

        printf("[DEBUG] Cleaned command: '%s'\n", buff);

        // Убираем лишние пробелы в начале и конце (Telnet может их добавлять)
        char* cmd = buff;
        while (*cmd == ' ') cmd++;

        char* end = cmd + strlen(cmd) - 1;
        while (end > cmd && *end == ' ') { *end = 0; end--; }

        if (strncmp(cmd, "load ", 5) == 0) {
            char* filename = cmd + 5;
            while (*filename == ' ') filename++; // Убираем пробелы после "load"

            if (strstr(filename, "..") || strchr(filename, '/') || strchr(filename, '\\')) {
                const char* err = "ERROR: Invalid filename\r\n";
                send(my_sock, err, (int)strlen(err), 0);
                total_recv = 0;
                continue;
            }

            FILE* check = fopen(filename, "rb");
            if (check == NULL) {
                const char* err_msg = "ERROR: File not found\r\n";
                send(my_sock, err_msg, (int)strlen(err_msg), 0);
                printf("[-] File %s not found. Disconnecting.\n", filename);
                break; // РАЗРЫВАЕМ СОЕДИНЕНИЕ
            }
            else {
                fclose(check);
                SendFileToClient(my_sock, filename);
            }
        }
        else {
            const char* err = "Unknown command. Use: load <filename>\r\n";
            send(my_sock, err, (int)strlen(err), 0);
        }

        total_recv = 0; // Сброс буфера
    }

    printf("- Client disconnected.\n");
    closesocket(my_sock);
    return 0;
}

int main() {
    // АВТОМАТИЧЕСКОЕ СОЗДАНИЕ ФАЙЛА ДЛЯ ТЕСТА
    FILE* test_f = fopen("test.txt", "w");
    if (test_f) {
        fprintf(test_f, "Hello! This is a test file for Variant 1.\nLine 2.");
        fclose(test_f);
        printf("[INFO] Created test.txt automatically in the executable folder.\n");
    }

    WSADATA wsaData;
    if (WSAStartup(0x0202, &wsaData)) return 1;

    SOCKET listen_sock = socket(AF_INET, SOCK_STREAM, 0);
    sockaddr_in local_addr;
    local_addr.sin_family = AF_INET;
    local_addr.sin_port = htons(MY_PORT);
    local_addr.sin_addr.s_addr = INADDR_ANY;

    bind(listen_sock, (sockaddr*)&local_addr, sizeof(local_addr));
    listen(listen_sock, SOMAXCONN);

    printf("TCP Server (Variant 1) listening on port %d...\n", MY_PORT);
    printf("Waiting for connections...\n");

    while (1) {
        sockaddr_in client_addr;
        int client_size = sizeof(client_addr);
        SOCKET client_sock = accept(listen_sock, (sockaddr*)&client_addr, &client_size);

        if (client_sock != INVALID_SOCKET) {
            printf("+ New connection from %s\n", inet_ntoa(client_addr.sin_addr));
            DWORD thID;
            CreateThread(NULL, 0, WorkWithClient, &client_sock, 0, &thID);
        }
    }

    closesocket(listen_sock);
    WSACleanup();
    return 0;
}