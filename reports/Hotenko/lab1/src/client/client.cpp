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