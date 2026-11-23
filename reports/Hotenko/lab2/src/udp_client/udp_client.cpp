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

class UdpClient {
public:
    UdpClient() : m_socket(INVALID_SOCKET), m_isConnected(false) {
        WSADATA wsaData;
        if (WSAStartup(MAKEWORD(2, 2), &wsaData) != 0) {
            throw std::runtime_error("WSAStartup failed. Error: " + std::to_string(WSAGetLastError()));
        }
        m_logFile.open("udp_client_protocol.log", std::ios::app);
    }

    ~UdpClient() {
        disconnect();
        WSACleanup();
        if (m_logFile.is_open()) m_logFile.close();
    }

    void connectToServer(const std::string& serverIp, int port) {
        m_socket = socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP);
        if (m_socket == INVALID_SOCKET) {
            throw std::runtime_error("Socket creation failed. Error: " + std::to_string(WSAGetLastError()));
        }

        // Устанавливаем таймаут на прием (2 секунды). Если сервер не ответит - клиент не зависнет.
        DWORD timeout = 2000;
        setsockopt(m_socket, SOL_SOCKET, SO_RCVTIMEO, (const char*)&timeout, sizeof(timeout));

        m_serverAddr = {};
        m_serverAddr.sin_family = AF_INET;
        m_serverAddr.sin_port = htons(port);

        if (inet_pton(AF_INET, serverIp.c_str(), &m_serverAddr.sin_addr) != 1) {
            addrinfo hints{}, *res = nullptr;
            hints.ai_family = AF_INET;
            if (getaddrinfo(serverIp.c_str(), nullptr, &hints, &res) == 0 && res != nullptr) {
                m_serverAddr.sin_addr = reinterpret_cast<sockaddr_in*>(res->ai_addr)->sin_addr;
                freeaddrinfo(res);
            } else {
                closesocket(m_socket);
                m_socket = INVALID_SOCKET;
                throw std::runtime_error("Invalid address or host not found: " + serverIp);
            }
        }

        m_isConnected = true;
        std::string startTime = getCurrentTime();
        std::cout << "UDP Connection established with " << serverIp << ":" << port << ".\n";
        std::cout << "Start time: " << startTime << "\n\n";
        logEvent("START CONNECTION: " + startTime);
        std::cout << "Type text and press Enter (or PgDn) to send.\n";
        std::cout << "Type 'quit' to exit.\n\n> ";
    }

    void runCommunicationLoop() {
        if (!m_isConnected) {
            std::cout << "Not connected. Use 'connect <ip> <port>' first.\n";
            return;
        }

        std::string userInput = "";

        while (m_isConnected) {
            if (_kbhit()) {
                int ch = _getch();
                if (ch == 0 || ch == 224) {
                    int key = _getch();
                    if (key == 81) { // PgDn
                        if (!userInput.empty()) sendMessage(userInput);
                    }
                } else if (ch == '\r') { // Enter
                    if (!userInput.empty()) {
                        if (userInput == "quit") {
                            std::cout << "\nExiting...\n";
                            return;
                        }
                        sendMessage(userInput);
                    }
                } else if (ch == '\b') {
                    if (!userInput.empty()) {
                        userInput.pop_back();
                        std::cout << "\b \b";
                    }
                } else if (ch >= 32 && ch <= 126) {
                    userInput += static_cast<char>(ch);
                    std::cout << static_cast<char>(ch);
                }
            }
        }
    }

    void disconnect() {
        if (m_socket != INVALID_SOCKET) {
            std::string endTime = getCurrentTime();
            std::cout << "\nEnd time: " << endTime << "\n";
            logEvent("END CONNECTION: " + endTime);
            closesocket(m_socket);
            m_socket = INVALID_SOCKET;
            m_isConnected = false;
        }
    }

private:
    SOCKET m_socket;
    sockaddr_in m_serverAddr;
    bool m_isConnected;
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
        if (m_logFile.is_open()) m_logFile << event << std::endl;
    }

    void sendMessage(std::string& userInput) {
        // Используем sendto, так как UDP требует явного указания адреса
        if (sendto(m_socket, userInput.c_str(), (int)userInput.size(), 0,
                   reinterpret_cast<sockaddr*>(&m_serverAddr), sizeof(m_serverAddr)) == SOCKET_ERROR) {
            std::cerr << "\nSend failed. Error: " << WSAGetLastError() << "\n";
        } else {
            std::string sendTime = getCurrentTime();
            std::cout << "\n[Sent] " << userInput << "\n";
            logEvent("SENT: " + userInput + " | Time: " + sendTime);
            
            // Ждем ответ от сервера
            char recvBuf[2048];
            sockaddr_in fromAddr{};
            int fromAddrSize = sizeof(fromAddr);
            
            int bytesRecv = recvfrom(m_socket, recvBuf, sizeof(recvBuf) - 1, 0, 
                                     reinterpret_cast<sockaddr*>(&fromAddr), &fromAddrSize);
            
            if (bytesRecv > 0) {
                recvBuf[bytesRecv] = '\0';
                std::string response(recvBuf);
                
                if (response == "Session ended by double dot.") {
                    std::cout << "\n[Server closed session]\n";
                    m_isConnected = false; // Возвращаемся в главное меню
                } else {
                    std::string recvTime = getCurrentTime();
                    std::cout << "[Server] " << response << "\n";
                    logEvent("RECEIVED: " + response + " | Time: " + recvTime);
                }
            } else if (bytesRecv == SOCKET_ERROR) {
                // Если сработал таймаут (WSAETIMEDOUT)
                if (WSAGetLastError() == WSAETIMEDOUT) {
                    std::cout << "[!] Timeout: Server did not respond.\n";
                } else {
                    std::cout << "[!] Recv error: " << WSAGetLastError() << "\n";
                }
            }
        }
        userInput.clear();
        if (m_isConnected) std::cout << "> ";
    }
};

int main() {
    setlocale(LC_ALL, "RU");
    std::cout << "UDP CLIENT (Variant 13)\n";
    std::cout << "Commands:\n";
    std::cout << "  connect <ip> <port> - connect to server\n";
    std::cout << "  quit                - exit\n\n";

    try {
        UdpClient client;
        std::string command;

        while (true) {
            std::getline(std::cin, command);

            if (command == "quit") {
                break;
            } else if (command.rfind("connect ", 0) == 0) {
                std::istringstream iss(command);
                std::string cmd, ip;
                int port;
                iss >> cmd >> ip >> port;
                if (iss.fail()) {
                    std::cout << "Usage: connect <ip> <port>\n> ";
                    continue;
                }
                try {
                    client.connectToServer(ip, port);
                    client.runCommunicationLoop();
                } catch (const std::exception& ex) {
                    std::cerr << "Error: " << ex.what() << "\n> ";
                }
                client.disconnect();
                std::cout << "> ";
            } else {
                std::cout << "Unknown command.\n> ";
            }
        }
    } catch (const std::exception& ex) {
        std::cerr << "Critical error: " << ex.what() << "\n";
        return -1;
    }

    return 0;
}