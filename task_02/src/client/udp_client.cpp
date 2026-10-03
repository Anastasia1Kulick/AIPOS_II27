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

class UdpClient {
public:
    UdpClient(const std::string& serverIp, int port)
        : m_serverIp(serverIp), m_port(port), m_socket(INVALID_SOCKET)
    {
        WSADATA wsaData;
        if (WSAStartup(MAKEWORD(2, 2), &wsaData) != 0) {
            throw std::runtime_error("WSAStartup failed. Error: " + std::to_string(WSAGetLastError()));
        }
    }

    ~UdpClient() {
        disconnect();
        WSACleanup();
    }

    void initializeConnection() {
        m_socket = socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP);
        if (m_socket == INVALID_SOCKET) {
            throw std::runtime_error("Socket creation failed. Error: " + std::to_string(WSAGetLastError()));
        }

        sockaddr_in destAddr{};
        destAddr.sin_family = AF_INET;
        destAddr.sin_port = htons(m_port);

        if (inet_pton(AF_INET, m_serverIp.c_str(), &destAddr.sin_addr) != 1) {
            addrinfo hints{}, * res = nullptr;
            hints.ai_family = AF_INET;
            hints.ai_socktype = SOCK_DGRAM;

            if (getaddrinfo(m_serverIp.c_str(), nullptr, &hints, &res) == 0 && res != nullptr) {
                destAddr.sin_addr = reinterpret_cast<sockaddr_in*>(res->ai_addr)->sin_addr;
                freeaddrinfo(res);
            }
            else {
                closesocket(m_socket);
                m_socket = INVALID_SOCKET;
                throw std::runtime_error("Invalid address or host not found: " + m_serverIp);
            }
        }

        if (connect(m_socket, reinterpret_cast<sockaddr*>(&destAddr), sizeof(destAddr)) == SOCKET_ERROR) {
            closesocket(m_socket);
            m_socket = INVALID_SOCKET;
            throw std::runtime_error("Connect failed. Error: " + std::to_string(WSAGetLastError()));
        }

        std::cout << "UDP Client initialized for target " << m_serverIp << ":" << m_port << "\n";
        std::cout << "Type ~#~ to quit\n\n";
    }

    void runCommunicationLoop() {
        if (m_socket == INVALID_SOCKET) {
            std::cerr << "Нет активного сокета для отправки данных.\n";
            return;
        }
        else {
            auto now = std::chrono::system_clock::now();

            std::time_t currentTime = std::chrono::system_clock::to_time_t(now);

            
            std::tm localTime;
            localtime_s(&localTime, &currentTime);

        
            std::cout << "Start time: "
                << std::put_time(&localTime, "%Y-%m-%d %H:%M:%S")
                << std::endl;
        }

       

        std::vector<char> buffer(1024);
        int bytesRecv = 0;

        auto AddToStr = [](const std::vector<char> &buffer, int size,std::string &str) {
                for (int i = 0; i < size; ++i) {
                    str += buffer[i];
                }
            return 0;
        };

        std::string str;
        int res = 0;
         std::cout << "Send to server:  ";
                std::string userInput;
                std::getline(std::cin, userInput);

                userInput += "\r\n";

       
                if (send(m_socket, userInput.c_str(), static_cast<int>(userInput.size()), 0) == SOCKET_ERROR) {
                    std::cerr << "Send failed. Error: " << WSAGetLastError() << "\n";
                    disconnect();
        }
        while ((bytesRecv = recv(m_socket, buffer.data(), static_cast<int>(buffer.size() - 1), 0)) > 0) {
            
            AddToStr(buffer,bytesRecv,str);
            if(str.back() == '\n') str.pop_back();
            if(str == "-1"){
                disconnect();
                break;
            } 
            
            size_t delimiterPos = str.find(':');

            if (delimiterPos != std::string::npos) {
            
            std::string partRes = str.substr(0, delimiterPos);           
            std::string partTotalSize = str.substr(delimiterPos + 1);
            std::cout << "From server got result: " << std::stoi(partRes) << "Total send bytes: "<<  std::stoi(partTotalSize) << std::endl;
            } 
          
            std::cout << "Send to server:  ";
            std::string userInput;
            std::getline(std::cin, userInput);

            userInput += "\r\n";
                str.clear();
                    if (send(m_socket, userInput.c_str(), static_cast<int>(userInput.size()), 0) == SOCKET_ERROR) {
                        std::cerr << "Send failed. Error: " << WSAGetLastError() << "\n";
                        break;
                    }

        if (bytesRecv == SOCKET_ERROR) {
            std::cerr << "Recv error: " << WSAGetLastError() << "\n";
            disconnect();
            break;
        }

    }
}

    void disconnect() {
        if (m_socket != INVALID_SOCKET) {
            closesocket(m_socket);
            logTime("End time");
            m_socket = INVALID_SOCKET;
        }
    }

private:
    void logTime(const std::string& message) {
        auto now = std::chrono::system_clock::now();
        std::time_t currentTime = std::chrono::system_clock::to_time_t(now);
        std::tm localTime;
        localtime_s(&localTime, &currentTime);
        std::cout << message << ": " << std::put_time(&localTime, "%Y-%m-%d %H:%M:%S") << std::endl;
    }

    std::string m_serverIp;
    int m_port;
    SOCKET m_socket;
};

int main() {
    setlocale(LC_ALL, "RU");
    std::cout << "UDP CLIENT\n";

    try {
        UdpClient client("127.0.0.1", 666);
        client.initializeConnection();
        client.runCommunicationLoop();
    }
    catch (const std::exception& ex) {
        std::cerr << "Critical error: " << ex.what() << "\n";
        return -1;
    }

    return 0;
}
