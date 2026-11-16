<p align="center">Министерство образования Республики Беларусь</p>
<p align="center">Учреждение образования</p>
<p align="center">“Брестский Государственный технический университет”</p>
<p align="center">Кафедра ИИТ</p>
<br><br><br><br><br><br><br>
<p align="center">Лабораторная работа №1</p>
<p align="center">По дисциплине “Аппаратное и Программное обеспечение сетей”</p>
<p align="center">Тема: “Организация TCP – сервера/клиента”</p>
<p align="center">Вариант: 13</p>
<br><br><br><br><br>
<p align="right">Выполнил:</p>
<p align="right">Студент 3 курса</p>
<p align="right">Группы ИИ-27</p>
<p align="right">Хотенко В.В.</p>
<p align="right">Проверил:</p>
<p align="right">Кулик А.Д.</p>
<br><br><br><br><br>
<p align="center">Брест 2026</p>

# Цель

Реализовать на языке программирования высокого уровня Java клент-серверное взамодействие, организованной через TCP.

# Задание на выполнение

8.1 Изучить теоретический материал, функции
WinSockи листинг программы реализации TCP-сервера. Получить индивидуальное задание у преподавателя.
8.2 Используя систему Borland C++ v.5.02 (\bc5), разработать программу работы TCP-эхо-сервера, выполняющую функции согласно варианта задания (см. приложения). В качестве клиента использовать программу telnet. Выполнить проверку программы согласно методике,
приведенной в разделе 7.1. Продемонстрировать работу системы преподавателю.
8.3 Представить отчет, содержащий титульный лист, листинг программы с подробными комментариями основных фрагментов программы.
8.4 Подготовиться к защите лабораторной работы по теоретическому материалу, функциям WinSock 2, собственным результатам, полученным в ходе выполнения лабораторной работы
8.5 Изучить теоретический материал, функции WinSock и листинг программ реализации TCP-клиента.
8.6 Используя систему Borland C++ v.5.02 (\bc5), разработать программу работы TCP-клиента (см. приложения). Выполнить проверку программы с использованием разработанного в предыдущей лабораторной работе TCP-сервера. Продемонстрироватьработу системы преподавателю.
8.7 Представить отчет, содержащий титульный лист, листинг программы с подробнымикомментариями основных фрагментов программы.
8.8 Подготовиться к защите лабораторной работы по теоретическому материалу, функциям WinSock2, собственным результатам, полученным в ходе выполнения лабораторной работы.

# Структура программы

reports/Hotenkok/lab1/
├── rep/
│   └── README.md
├── src/
│   ├── client
│   │   ├── client.cpp
│   │   └── CMakeLists.txt
│   └── server
│       ├── server.cpp
│       └── CMakeLists.txt
├── client_protocol.log
├── CMakeLists.txt
├── build.bat
├── run_client.bat
└── run_server.bat

# Код программы
## Клиентская часть

#include <iostream>
#include <string>
#include <vector>
#include <winsock2.h>
#include <windows.h>
#include <ws2tcpip.h>
#include <chrono>
#include <iomanip>
#include <fstream>
#include <conio.h>
#include <sstream>

#pragma comment(lib, "ws2_32.lib")

class TcpClient {
public:
    TcpClient() : m_socket(INVALID_SOCKET) {
        WSADATA wsaData;
        if (WSAStartup(MAKEWORD(2, 2), &wsaData) != 0) {
            throw std::runtime_error("WSAStartup failed. Error: " + std::to_string(WSAGetLastError()));
        }
        m_logFile.open("client_protocol.log", std::ios::app);
    }

    ~TcpClient() {
        disconnect();
        WSACleanup();
        if (m_logFile.is_open()) m_logFile.close();
    }

    void connectToServer(const std::string& serverIp, int port) {
        if (m_socket != INVALID_SOCKET) {
            std::cout << "Already connected. Use 'quit' to disconnect.\n";
            return;
        }

        m_socket = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP);
        if (m_socket == INVALID_SOCKET) {
            throw std::runtime_error("Socket creation failed. Error: " + std::to_string(WSAGetLastError()));
        }

        sockaddr_in destAddr{};
        destAddr.sin_family = AF_INET;
        destAddr.sin_port = htons(port);

        if (inet_pton(AF_INET, serverIp.c_str(), &destAddr.sin_addr) != 1) {
            addrinfo hints{}, *res = nullptr;
            hints.ai_family = AF_INET;
            if (getaddrinfo(serverIp.c_str(), nullptr, &hints, &res) == 0 && res != nullptr) {
                destAddr.sin_addr = reinterpret_cast<sockaddr_in*>(res->ai_addr)->sin_addr;
                freeaddrinfo(res);
            } else {
                closesocket(m_socket);
                m_socket = INVALID_SOCKET;
                throw std::runtime_error("Invalid address or host not found: " + serverIp);
            }
        }

        if (connect(m_socket, reinterpret_cast<sockaddr*>(&destAddr), sizeof(destAddr)) == SOCKET_ERROR) {
            closesocket(m_socket);
            m_socket = INVALID_SOCKET;
            throw std::runtime_error("Connect failed. Error: " + std::to_string(WSAGetLastError()));
        }

        std::string startTime = getCurrentTime();
        std::cout << "Connection with " << serverIp << ":" << port << " established.\n";
        std::cout << "Start time: " << startTime << "\n\n";
        logEvent("START CONNECTION: " + startTime);
        std::cout << "Press PgDn to send a message, or type 'quit' to exit.\n";
    }

    void runCommunicationLoop() {
        if (m_socket == INVALID_SOCKET) {
            std::cout << "Not connected. Use 'connect <ip> <port>' first.\n";
            return;
        }

        fd_set readfds;
        timeval timeout;
        timeout.tv_sec = 0;
        timeout.tv_usec = 100000; // 100ms

        std::string userInput = "";

        while (true) {
            // Неблокирующее чтение сокета
            FD_ZERO(&readfds);
            FD_SET(m_socket, &readfds);

            int selectRes = select(0, &readfds, nullptr, nullptr, &timeout);
            if (selectRes == SOCKET_ERROR) {
                std::cerr << "Select error: " << WSAGetLastError() << "\n";
                break;
            }

            if (selectRes > 0 && FD_ISSET(m_socket, &readfds)) {
                char buffer[1024];
                int bytesRecv = recv(m_socket, buffer, sizeof(buffer) - 1, 0);
                if (bytesRecv > 0) {
                    buffer[bytesRecv] = '\0';
                    std::string receivedMsg(buffer);

                    if (receivedMsg == "DISCONNECT\n" || receivedMsg == "DISCONNECT\r\n") {
                        std::cout << "\n[Server closed connection]\n";
                        break;
                    }

                    std::string recvTime = getCurrentTime();
                    std::cout << "\n[Server] " << receivedMsg;
                    logEvent("RECEIVED: " + receivedMsg + " | Time: " + recvTime);
                } else if (bytesRecv == 0) {
                    std::cout << "\nServer disconnected.\n";
                    break;
                } else {
                    std::cerr << "\nRecv error: " << WSAGetLastError() << "\n";
                    break;
                }
            }

            // Обработка ввода пользователя (PgDn)
            if (_kbhit()) {
                int ch = _getch();
                if (ch == 0 || ch == 224) {
                    int key = _getch(); // Считываем код клавиши
                    if (key == 81) { // Код PgDn (0x51)
                        if (!userInput.empty()) {
                            std::string msgToSend = userInput + "\r\n";
                            if (send(m_socket, msgToSend.c_str(), (int)msgToSend.size(), 0) == SOCKET_ERROR) {
                                std::cerr << "Send failed. Error: " << WSAGetLastError() << "\n";
                                break;
                            }
                            std::string sendTime = getCurrentTime();
                            std::cout << "\n[Sent] " << userInput << "\n";
                            logEvent("SENT: " + userInput + " | Time: " + sendTime);
                            userInput.clear();
                        } else {
                            std::cout << "\n[!] Buffer is empty.\n";
                        }
                    }
                } else if (ch == '\b') { // Backspace
                    if (!userInput.empty()) {
                        userInput.pop_back();
                        std::cout << "\b \b";
                    }
                } else if (ch >= 32 && ch <= 126) { // Печатные символы
                    userInput += static_cast<char>(ch);
                    std::cout << static_cast<char>(ch);
                } else if (ch == '\r') {
                    std::cout << "\n(Use PgDn to send)\n";
                }
            }
        }
    }

    void disconnect() {
        if (m_socket != INVALID_SOCKET) {
            std::string endTime = getCurrentTime();
            std::cout << "End time: " << endTime << "\n";
            logEvent("END CONNECTION: " + endTime);
            
            closesocket(m_socket);
            m_socket = INVALID_SOCKET;
        }
    }

private:
    std::string m_serverIp;
    int m_port;
    SOCKET m_socket;
    std::ofstream m_logFile;

    std::string getCurrentTime() {
        auto now = std::chrono::system_clock::now();
        std::time_t currentTime = std::chrono::system_clock::to_time_t(now);
        std::tm localTime;
        localtime_s(&localTime, &currentTime);
        std::ostringstream oss;
        oss << std::put_time(&localTime, "%Y-%m-%d %H:%M:%S");
        return oss.str();
    }

    void logEvent(const std::string& event) {
        if (m_logFile.is_open()) {
            m_logFile << event << std::endl;
        }
    }
};

int main() {
    setlocale(LC_ALL, "RU");
    std::cout << "TCP CLIENT (Variant 13)\n";
    std::cout << "Commands:\n";
    std::cout << "  connect <ip> <port> - connect to server\n";
    std::cout << "  quit                - exit\n";
    std::cout << "  PgDn                - send typed text\n\n";

    try {
        TcpClient client;
        std::string command;

        while (true) {
            std::cout << "> ";
            std::getline(std::cin, command);

            if (command == "quit") {
                break;
            } else if (command.rfind("connect ", 0) == 0) {
                std::istringstream iss(command);
                std::string cmd, ip;
                int port;
                iss >> cmd >> ip >> port;
                if (iss.fail()) {
                    std::cout << "Usage: connect <ip> <port>\n";
                    continue;
                }
                try {
                    client.connectToServer(ip, port);
                    client.runCommunicationLoop();
                } catch (const std::exception& ex) {
                    std::cerr << "Error: " << ex.what() << "\n";
                }
                client.disconnect();
            } else {
                std::cout << "Unknown command.\n";
            }
        }
    } catch (const std::exception& ex) {
        std::cerr << "Critical error: " << ex.what() << "\n";
        return -1;
    }

    return 0;
}

## Серверная часть

#include <iostream>
#include <string>
#include <atomic>
#include <vector>
#include <winsock2.h>
#include <windows.h>
#include <ws2tcpip.h>
#include <sstream>
#include <algorithm>

#pragma comment(lib, "ws2_32.lib")

class TcpServer {
private:
    struct ThreadParam {
        TcpServer* serverPointer;
        SOCKET clientSocket;
    };

public:
    TcpServer(int port) : m_port(port), m_listenSocket(INVALID_SOCKET), m_nclients(0) {
        WSADATA wsaData;
        if (WSAStartup(MAKEWORD(2, 2), &wsaData) != 0) {
            throw std::runtime_error("WSAStartup failed. Error: " + std::to_string(WSAGetLastError()));
        }
    }

    ~TcpServer() {
        if (m_listenSocket != INVALID_SOCKET) {
            closesocket(m_listenSocket);
        }
        WSACleanup();
    }

    void start() {
        m_listenSocket = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP);
        if (m_listenSocket == INVALID_SOCKET) {
            throw std::runtime_error("Socket creation failed. Error: " + std::to_string(WSAGetLastError()));
        }

        int opt = 1;
        setsockopt(m_listenSocket, SOL_SOCKET, SO_REUSEADDR, (const char*)&opt, sizeof(opt));

        sockaddr_in localAddr{};
        localAddr.sin_family = AF_INET;
        localAddr.sin_port = htons(m_port);
        localAddr.sin_addr.s_addr = INADDR_ANY;

        if (bind(m_listenSocket, reinterpret_cast<sockaddr*>(&localAddr), sizeof(localAddr)) == SOCKET_ERROR) {
            throw std::runtime_error("Bind failed. Error: " + std::to_string(WSAGetLastError()));
        }

        if (listen(m_listenSocket, SOMAXCONN) == SOCKET_ERROR) {
            throw std::runtime_error("Listen failed. Error: " + std::to_string(WSAGetLastError()));
        }

        std::cout << "TCP Server (Variant 13) started on port " << m_port << "\n";
        std::cout << "Waiting for connections...\n";

        run();
    }

private:
    int m_port;
    SOCKET m_listenSocket;
    std::atomic<int> m_nclients;

    void run() {
        while (true) {
            sockaddr_in clientAddr{};
            int clientAddrSize = sizeof(clientAddr);
            SOCKET clientSocket = accept(m_listenSocket, reinterpret_cast<sockaddr*>(&clientAddr), &clientAddrSize);

            if (clientSocket == INVALID_SOCKET) {
                std::cerr << "Accept failed. Error: " << WSAGetLastError() << "\n";
                continue;
            }

            m_nclients++;
            char ipStr[INET_ADDRSTRLEN] = "";
            inet_ntop(AF_INET, &(clientAddr.sin_addr), ipStr, INET_ADDRSTRLEN);
            std::cout << "[+] Client connected: " << ipStr << " | Online: " << m_nclients.load() << "\n";

            ThreadParam* pParam = new ThreadParam{ this, clientSocket };
            HANDLE thID = CreateThread(nullptr, 0, clientThreadProxy, pParam, 0, nullptr);
            if (thID) {
                CloseHandle(thID);
            } else {
                std::cerr << "Failed to create thread\n";
                closesocket(clientSocket);
                delete pParam;
                m_nclients--;
            }
        }
    }

    static DWORD WINAPI clientThreadProxy(LPVOID lpParam) {
        if (!lpParam) return -1;
        ThreadParam* pParam = reinterpret_cast<ThreadParam*>(lpParam);
        TcpServer* server = pParam->serverPointer;
        SOCKET clientSock = pParam->clientSocket;
        delete pParam;

        server->handleClient(clientSock);
        return 0;
    }

    // Функция для разворота слов в предложении
    std::string reverseWords(const std::string& sentence) {
        std::istringstream iss(sentence);
        std::vector<std::string> words;
        std::string word;
        while (iss >> word) {
            words.push_back(word);
        }
        std::reverse(words.begin(), words.end());
        
        std::string result;
        for (size_t i = 0; i < words.size(); ++i) {
            result += words[i];
            if (i < words.size() - 1) result += " ";
        }
        return result;
    }

    void handleClient(SOCKET clientSocket) {
        std::string buffer; // Буфер для накопления данных
        char recvBuf[1024];
        int bytesRecv = 0;

        while ((bytesRecv = recv(clientSocket, recvBuf, sizeof(recvBuf), 0)) > 0) {
            buffer.append(recvBuf, bytesRecv);

            // Обрабатываем буфер, пока в нем есть данные
            while (!buffer.empty()) {
                size_t dotPos = buffer.find('.');

                if (dotPos != std::string::npos) {
                    // Проверяем на ".."
                    if (dotPos + 1 < buffer.size() && buffer[dotPos + 1] == '.') {
                        std::string msg = "Session ended by double dot.\n";
                        send(clientSocket, msg.c_str(), (int)msg.size(), 0);
                        closesocket(clientSocket);
                        m_nclients--;
                        std::cout << "[-] Client disconnected | Online: " << m_nclients.load() << "\n";
                        return;
                    }

                    // Извлекаем предложение (без точки)
                    std::string sentence = buffer.substr(0, dotPos);
                    buffer.erase(0, dotPos + 1); // Удаляем предложение и точку

                    // Убираем возможные \r и \n
                    sentence.erase(std::remove(sentence.begin(), sentence.end(), '\r'), sentence.end());
                    sentence.erase(std::remove(sentence.begin(), sentence.end(), '\n'), sentence.end());

                    if (!sentence.empty()) {
                        std::string reversed = reverseWords(sentence);
                        std::string response = reversed + "\n";
                        std::cout << "Received: [" << sentence << "] -> Sent: [" << reversed << "]\n";
                        send(clientSocket, response.c_str(), (int)response.size(), 0);
                    }
                } else {
                    // Точки нет, выходим из внутреннего цикла и ждем еще данных
                    break;
                }
            }
        }

        m_nclients--;
        std::cout << "[-] Client disconnected | Online: " << m_nclients.load() << "\n";
        closesocket(clientSocket);
    }
};

int main() {
    setlocale(LC_ALL, "RU");
    try {
        TcpServer server(666);
        server.start();
    } catch (const std::exception& ex) {
        std::cerr << "Critical error: " << ex.what() << "\n";
        return -1;
    }
    return 0;
}
