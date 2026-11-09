#include <winsock2.h>
#include <ws2tcpip.h>
#include <windows.h>
#include <stdio.h>
#include <string.h>
#include <time.h>
#include <conio.h> 

#pragma comment(lib, "ws2_32.lib")

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

    SOCKET sock = INVALID_SOCKET;
    char buffer[8192];
    int idx = 0;

    printf("Клиент запущен.\n");
    printf("Для подключения введите: connect <адрес> <порт> и нажмите Enter\n");
    printf("Для отправки сообщения наберите текст и нажмите клавишу END\n");
    printf("Для выхода нажмите ESC\n\n");

    while (1) {
        printf("> ");
        idx = 0;
        memset(buffer, 0, sizeof(buffer));

        while (1) {
            int ch = _getch();

            if (ch == 0 || ch == 224) {
                int special = _getch();
                // Код 79 - клавиша End, 77 - End на цифровом блоке (Numpad)
                if (special == 79 || special == 77) { 
                    buffer[idx] = '\0';
                    printf("\n[Нажата End] Отправка: %s\n", buffer);
                    
                    if (idx > 0 && sock != INVALID_SOCKET) {
                        LogEvent("SEND", buffer);
                        if (send(sock, buffer, (int)strlen(buffer), 0) != SOCKET_ERROR) {
                            int bytes_received = recv(sock, buffer, sizeof(buffer) - 1, 0);
                            if (bytes_received > 0) {
                                buffer[bytes_received] = '\0';
                                printf("[Ответ сервера] %s\n", buffer);
                                LogEvent("RECV", buffer);
                            }
                        }
                    }
                    break; 
                }
            }
            else if (ch == 13) { // Enter
                buffer[idx] = '\0';
                printf("\n");
                
                if (strncmp(buffer, "connect ", 8) == 0 && sock == INVALID_SOCKET) {
                    char ip[32];
                    int port;
                    if (sscanf(buffer + 8, "%31s %d", ip, &port) == 2) {
                        sock = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP);
                        sockaddr_in server_addr;
                        server_addr.sin_family = AF_INET;
                        server_addr.sin_port = htons(port);
                        inet_pton(AF_INET, ip, &server_addr.sin_addr);

                        if (connect(sock, (sockaddr*)&server_addr, sizeof(server_addr)) == SOCKET_ERROR) {
                            printf("Ошибка подключения: %d\n", WSAGetLastError());
                            closesocket(sock);
                            sock = INVALID_SOCKET;
                        } else {
                            printf("Успешно подключено к %s:%d\n", ip, port);
                            LogEvent("CONNECT", "Успешно");
                        }
                    } else {
                        printf("Неверный формат команды.\n");
                    }
                }
                break; 
            }
            else if (ch == 27) { // ESC для выхода
                goto cleanup;
            }
            else if (ch == 8) { // Backspace
                if (idx > 0) {
                    idx--;
                    printf("\b \b");
                }
            }
            else if (ch >= 32 && ch <= 126 && idx < sizeof(buffer) - 1) {
                buffer[idx++] = (char)ch;
                printf("%c", ch); 
            }
        }
    }

cleanup:
    if (sock != INVALID_SOCKET) closesocket(sock);
    WSACleanup();
    return 0;
}