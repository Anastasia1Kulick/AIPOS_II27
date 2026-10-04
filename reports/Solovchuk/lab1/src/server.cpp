#define _WINSOCK_DEPRECATED_NO_WARNINGS   
#define _CRT_SECURE_NO_WARNINGS
#include <stdio.h>
#include <iostream>
#include <stdlib.h>
#include <string.h>
#include <winsock2.h>
#include <windows.h>

#pragma comment(lib, "ws2_32.lib")

#define MY_PORT 666
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
