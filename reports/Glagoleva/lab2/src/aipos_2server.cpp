#define _CRT_SECURE_NO_WARNINGS
#define _WINSOCK_DEPRECATED_NO_WARNINGS

#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <winsock2.h>   // winsock2.h подключается РАНЬШЕ windows.h
#include <windows.h>

#pragma comment(lib, "ws2_32.lib")

#define PORT 666
#define BUF_SIZE (64 * 1024)

int main(int argc, char* argv[])
{
    char buff[BUF_SIZE];
    printf("UDP SERVER DEMO (variant 1: load <file>)\n");

    // ---------- Шаг 1. Инициализация Winsock ----------
    WSADATA wsaData;
    if (WSAStartup(MAKEWORD(2, 2), &wsaData) != 0)
    {
        printf("WSAStartup error: %d\n", WSAGetLastError());
        return -1;
    }

    // ---------- Шаг 2. Создание дейтаграммного сокета ----------
    SOCKET my_sock = socket(AF_INET, SOCK_DGRAM, 0);
    if (my_sock == INVALID_SOCKET)
    {
        printf("socket() error: %d\n", WSAGetLastError());
        WSACleanup();
        return -1;
    }

    // ---------- Шаг 3. bind ----------
    sockaddr_in local_addr;
    local_addr.sin_family = AF_INET;
    local_addr.sin_addr.s_addr = INADDR_ANY;
    local_addr.sin_port = htons(PORT);

    if (bind(my_sock, (sockaddr*)&local_addr, sizeof(local_addr)) != 0)
    {
        printf("bind error: %d\n", WSAGetLastError());
        closesocket(my_sock);
        WSACleanup();
        return -1;
    }
    printf("Server bind on port %d. Waiting datagrams...\n", PORT);

    // ---------- Шаг 4. Цикл приёма/обработки ----------
    while (1)
    {
        sockaddr_in client_addr;
        int client_addr_size = sizeof(client_addr);

        int bsize = recvfrom(my_sock, buff, sizeof(buff) - 1, 0,
            (sockaddr*)&client_addr, &client_addr_size);
        if (bsize == SOCKET_ERROR)
        {
            printf("recvfrom() error: %d\n", WSAGetLastError());
            continue;
        }
        buff[bsize] = 0;

        printf("+ [%s:%d] datagram (%d bytes): %s\n",
            inet_ntoa(client_addr.sin_addr),
            ntohs(client_addr.sin_port),
            bsize, buff);

        // ---- Разбор команды: load <имя_файла> ----
        char reply[BUF_SIZE];
        reply[0] = 0;

        char* p = buff;
        while (*p == ' ' || *p == '\t') p++;

        if (_strnicmp(p, "load", 4) == 0 && (p[4] == ' ' || p[4] == '\t'))
        {
            p += 4;
            while (*p == ' ' || *p == '\t') p++;

            // Имя файла — до пробела/таба/конца строки
            char fname[260];
            int fi = 0;
            while (*p && *p != ' ' && *p != '\t' && fi < (int)sizeof(fname) - 1)
                fname[fi++] = *p++;
            fname[fi] = 0;

            if (fi == 0)
            {
                strcpy(reply, "ERROR: file name is empty. SESSION END.");
            }
            else
            {
                FILE* f = fopen(fname, "rb");
                if (f == NULL)
                {
                    // Файла нет — сообщение об ошибке + разрыв сеанса
                    sprintf(reply,
                        "ERROR: file '%s' not found in server directory. "
                        "SESSION END.", fname);
                }
                else
                {
                    // Читаем содержимое файла и шлём клиенту
                    int total = 0;
                    int n;
                    while ((n = (int)fread(reply + total, 1,
                        sizeof(reply) - 1 - total, f)) > 0)
                    {
                        total += n;
                        if (total >= (int)sizeof(reply) - 1) break;
                    }
                    fclose(f);
                    reply[total] = 0;

                    if (total == 0)
                    {
                        sprintf(reply,
                            "ERROR: file '%s' is empty. SESSION END.", fname);
                    }
                }
            }
        }
        else
        {
            strcpy(reply, "ERROR: unknown command. Use: load <filename>");
        }

        // ---- Отправка ответа ----
        sendto(my_sock, reply, (int)strlen(reply), 0,
            (sockaddr*)&client_addr, sizeof(client_addr));

        printf("  -> reply (%d bytes)\n", (int)strlen(reply));

        // Если в ответе ERROR — по заданию сеанс завершается
        if (strstr(reply, "SESSION END") != NULL)
        {
            printf("  Session ended for [%s:%d]\n",
                inet_ntoa(client_addr.sin_addr),
                ntohs(client_addr.sin_port));
            // UDP не хранит состояние; сервер продолжает ждать
            // следующих датаграмм (новых сеансов).
        }
    }

    closesocket(my_sock);
    WSACleanup();
    return 0;
}