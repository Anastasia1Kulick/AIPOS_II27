#define _CRT_SECURE_NO_WARNINGS
#define _WINSOCK_DEPRECATED_NO_WARNINGS

#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <time.h>
#include <winsock2.h>
#include <windows.h>

#pragma comment(lib, "ws2_32.lib")

#define PORT       666
#define SERVERADDR "127.0.0.1"
#define BUF_SIZE   (10 * 1024)
#define LOGFILE    "protocol.log"

// ----- Вспомогательная функция: запись в журнал -----
static void Log(FILE* log, const char* fmt, ...)
{
    if (log == NULL) return;
    time_t t = time(NULL);
    struct tm* lt = localtime(&t);
    char ts[32];
    strftime(ts, sizeof(ts), "%Y-%m-%d %H:%M:%S", lt);

    va_list ap;
    va_start(ap, fmt);
    fprintf(log, "[%s] ", ts);
    vfprintf(log, fmt, ap);
    fprintf(log, "\n");
    va_end(ap);
    fflush(log);
}

// ----- Разбор строки disconnect <адрес> <порт> -----
// Возвращает 1, если команда распознана (и заполняет out_ip/out_port)
static int ParseDisconnect(const char* s,
    char* out_ip, int out_ip_sz, int* out_port)
{
    const char* p = s;
    while (*p == ' ' || *p == '\t') p++;
    if (_strnicmp(p, "disconnect", 10) != 0) return 0;
    if (p[10] != ' ' && p[10] != '\t') return 0;
    p += 10;
    while (*p == ' ' || *p == '\t') p++;

    int i = 0;
    while (*p && *p != ' ' && *p != '\t' && i < out_ip_sz - 1)
        out_ip[i++] = *p++;
    out_ip[i] = 0;

    while (*p == ' ' || *p == '\t') p++;
    *out_port = atoi(p);
    return (i > 0 && *out_port > 0);
}

int main(int argc, char* argv[])
{
    char buff[BUF_SIZE];

    // ---------- Шаг 1. Winsock ----------
    WSADATA wsaData;
    if (WSAStartup(MAKEWORD(2, 2), &wsaData) != 0)
    {
        printf("WSAStartup error: %d\n", WSAGetLastError());
        return -1;
    }

    // ---------- Шаг 2. Сокет ----------
    SOCKET my_sock = socket(AF_INET, SOCK_DGRAM, 0);
    if (my_sock == INVALID_SOCKET)
    {
        printf("socket() error: %d\n", WSAGetLastError());
        WSACleanup();
        return -1;
    }

    // ---------- Журнал событий ----------
    FILE* log = fopen(LOGFILE, "a");
    Log(log, "=== CLIENT START ===");

    // ---------- Шаг 3. Адрес сервера ----------
    sockaddr_in dest_addr;
    dest_addr.sin_family = AF_INET;
    dest_addr.sin_port = htons(PORT);
    dest_addr.sin_addr.s_addr = inet_addr(SERVERADDR);

    printf("UDP DEMO Client (variant 8). Server: %s:%d\n",
        SERVERADDR, PORT);
    printf("Enter text + Enter. Commands:\n");
    printf("  load <file>                     - request file\n");
    printf("  disconnect <address> <port>     - end session / reconnect\n");
    printf("  quit                            - exit\n\n");

    Log(log, "Session start with %s:%d", SERVERADDR, PORT);

    // ---------- Шаг 4. Цикл обмена ----------
    while (1)
    {
        printf("S<=C: ");
        fflush(stdout);

        if (fgets(buff, sizeof(buff) - 1, stdin) == NULL) break;

        int len = (int)strlen(buff);
        if (len > 0 && buff[len - 1] == '\n') buff[--len] = 0;

        // --- quit ---
        if (strcmp(buff, "quit") == 0) break;

        // --- disconnect <addr> <port> ---
        char new_ip[64];
        int  new_port = 0;
        if (ParseDisconnect(buff, new_ip, sizeof(new_ip), &new_port))
        {
            Log(log, "DISCONNECT from %s:%d, new target %s:%d",
                inet_ntoa(dest_addr.sin_addr),
                ntohs(dest_addr.sin_port),
                new_ip, new_port);

            dest_addr.sin_addr.s_addr = inet_addr(new_ip);
            if (dest_addr.sin_addr.s_addr == INADDR_NONE)
            {
                printf("Invalid address %s. Disconnect cancelled.\n", new_ip);
                continue;
            }
            dest_addr.sin_port = htons((u_short)new_port);

            printf("Session ended. New server: %s:%d\n", new_ip, new_port);
            Log(log, "Session start with %s:%d", new_ip, new_port);
            continue;
        }

        if (len == 0) continue;

        // --- отправка строки ---
        if (sendto(my_sock, buff, len, 0,
            (sockaddr*)&dest_addr, sizeof(dest_addr)) == SOCKET_ERROR)
        {
            printf("sendto() error: %d\n", WSAGetLastError());
            break;
        }
        Log(log, "SENT     to   %s:%d : %s",
            inet_ntoa(dest_addr.sin_addr),
            ntohs(dest_addr.sin_port), buff);

        // --- приём ответа ---
        sockaddr_in server_addr;
        int server_addr_size = sizeof(server_addr);
        int n = recvfrom(my_sock, buff, sizeof(buff) - 1, 0,
            (sockaddr*)&server_addr, &server_addr_size);
        if (n == SOCKET_ERROR)
        {
            printf("recvfrom() error: %d\n", WSAGetLastError());
            break;
        }
        buff[n] = 0;

        printf("S=>C: %s\n", buff);
        Log(log, "RECEIVED from %s:%d : %s",
            inet_ntoa(server_addr.sin_addr),
            ntohs(server_addr.sin_port), buff);

        // Признак конца сеанса от сервера
        if (strstr(buff, "SESSION END") != NULL)
        {
            Log(log, "Session ended by server");
            printf("Session ended by server. Send 'disconnect <addr> <port>' "
                "to open a new one.\n");
        }
    }

    // ---------- Шаг 5. Завершение ----------
    Log(log, "Session end with %s:%d",
        inet_ntoa(dest_addr.sin_addr),
        ntohs(dest_addr.sin_port));
    Log(log, "=== CLIENT STOP ===");
    if (log) fclose(log);

    closesocket(my_sock);
    WSACleanup();
    return 0;
}