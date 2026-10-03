#include <iostream>
#include <string>
#include <vector>
#include <winsock2.h>
#include <windows.h>
#include <ws2tcpip.h> 
#include <chrono>
#include <iomanip> 
#include <stdexcept>

#pragma comment(lib, "ws2_32.lib")

class UdpServer {
public:
    UdpServer(int port)
        : m_port(port), m_socket(INVALID_SOCKET)
    {
        WSADATA wsaData;
        if (WSAStartup(MAKEWORD(2, 2), &wsaData) != 0) {
            throw std::runtime_error("WSAStartup failed. Error: " + std::to_string(WSAGetLastError()));
        }
    }

    ~UdpServer() {
        stop();
        WSACleanup();
    }

    void start() {
        m_socket = socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP);
        if (m_socket == INVALID_SOCKET) {
            throw std::runtime_error("Socket creation failed. Error: " + std::to_string(WSAGetLastError()));
        }

        sockaddr_in localAddr{};
        localAddr.sin_family = AF_INET;
        localAddr.sin_addr.s_addr = INADDR_ANY;
        localAddr.sin_port = htons(m_port);

        if (bind(m_socket, reinterpret_cast<sockaddr*>(&localAddr), sizeof(localAddr)) == SOCKET_ERROR) {
            closesocket(m_socket);
            m_socket = INVALID_SOCKET;
            throw std::runtime_error("Bind failed. Error: " + std::to_string(WSAGetLastError()));
        }

        std::cout << "UDP echo-Server started on port " << m_port << "\n\n";
    }

    void runEchoLoop() {
        if (m_socket == INVALID_SOCKET) {
            std::cerr << "Сервер не запущен.\n";
            return;
        }

        logTime("Server loop started");

        std::vector<char> buffer(1024);
        sockaddr_in clientAddr{};
        int clientAddrSize = sizeof(clientAddr);

        while (true) {
            int bytesRecv = recvfrom(m_socket, buffer.data(), static_cast<int>(buffer.size() - 1), 0,
                                     reinterpret_cast<sockaddr*>(&clientAddr), &clientAddrSize);

            if (bytesRecv == SOCKET_ERROR) {
                std::cerr << "Recvfrom error: " << WSAGetLastError() << "\n";
                break;
            }

            buffer[bytesRecv] = '\0';

           
            char ipStr[INET_ADDRSTRLEN];
            inet_ntop(AF_INET, &(clientAddr.sin_addr), ipStr, INET_ADDRSTRLEN);

            char hostName[NI_MAXHOST];
            if (getnameinfo(reinterpret_cast<sockaddr*>(&clientAddr), clientAddrSize,
                            hostName, NI_MAXHOST, nullptr, 0, 0) != 0) {
                std::string("Unknown host").copy(hostName, 12);
                hostName[12] = '\0';
            }

            auto now = std::chrono::system_clock::now();
            std::time_t currentTime = std::chrono::system_clock::to_time_t(now);
            std::tm localTime;
            localtime_s(&localTime, &currentTime);

            std::cout << "[" << std::put_time(&localTime, "%Y-%m-%d %H:%M:%S") << "] "
                      << "+" << hostName << " [" << ipStr << ":" << ntohs(clientAddr.sin_port) << "] new DATAGRAM!\n";
            std::cout << "C=>S: " << buffer.data() << "\n";

      
            if (sendto(m_socket, buffer.data(), bytesRecv, 0,
                       reinterpret_cast<sockaddr*>(&clientAddr), clientAddrSize) == SOCKET_ERROR) {
                std::cerr << "Sendto failed. Error: " << WSAGetLastError() << "\n";
            }
        }

        stop();
    }

    void stop() {
        if (m_socket != INVALID_SOCKET) {
            closesocket(m_socket);
            logTime("Server stopped");
            m_socket = INVALID_SOCKET;
        }
    }

   

private:
    void logTime(const std::string& message) {
        auto now = std::chrono::system_clock::now();
        std::time_t currentTime = std::chrono::system_clock::to_time_t(now);
        std::tm localTime;
        localtime_s(&localTime, &currentTime);
        std::cout << message << " at: " << std::put_time(&localTime, "%Y-%m-%d %H:%M:%S") << std::endl;
    }

    int m_port;
    SOCKET m_socket;
};

int main() {
    setlocale(LC_ALL, "RU");
    std::cout << "UDP SERVER\n";

    try {
        UdpServer server(666);
        server.start();
        server.runEchoLoop();
    }
    catch (const std::exception& ex) {
        std::cerr << "Critical error: " << ex.what() << "\n";
        return -1;
    }

    return 0;
}
