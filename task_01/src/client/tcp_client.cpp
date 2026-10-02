#include <iostream>
#include <string>
#include <vector>
#include <winsock2.h>
#include <windows.h>
#include <ws2tcpip.h> 
#include <chrono>
#include <iomanip> 

#pragma comment(lib, "ws2_32.lib")

class TcpClient {
public:
   
    TcpClient(const std::string& serverIp, int port)
        : m_serverIp(serverIp), m_port(port), m_socket(INVALID_SOCKET)
    {
        WSADATA wsaData;
        if (WSAStartup(MAKEWORD(2, 2), &wsaData) != 0) {
            throw std::runtime_error("WSAStartup failed. Error: " + std::to_string(WSAGetLastError()));
        }
    }

    
    ~TcpClient() {
        disconnect();
        WSACleanup();
    }

    
    void connectToServer() {
        m_socket = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP);
        if (m_socket == INVALID_SOCKET) {
            throw std::runtime_error("Socket creation failed. Error: " + std::to_string(WSAGetLastError()));
        }

        sockaddr_in destAddr{};
        destAddr.sin_family = AF_INET;
        destAddr.sin_port = htons(m_port);

      
        if (inet_pton(AF_INET, m_serverIp.c_str(), &destAddr.sin_addr) != 1) {
            addrinfo hints{}, * res = nullptr;
            hints.ai_family = AF_INET;

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

        std::cout << "Connection with " << m_serverIp << " success\n";
        std::cout << "Type quit for quit\n\n";
    }


    void runCommunicationLoop() {
        if (m_socket == INVALID_SOCKET) {
            std::cerr << "Нет активного соединения с сервером.\n";
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

       
        while ((bytesRecv = recv(m_socket, buffer.data(), static_cast<int>(buffer.size() - 1), 0)) > 0) {
            buffer[bytesRecv] = '\0'; 


            auto now = std::chrono::system_clock::now();

            std::time_t currentTime = std::chrono::system_clock::to_time_t(now);


            std::tm localTime;
            localtime_s(&localTime, &currentTime);

            std::cout << "Recieved msg from server at "
                << std::put_time(&localTime, "%Y-%m-%d %H:%M:%S") << "\t" << buffer.data();

           
            std::cout << "Send to server:  ";
            std::string userInput;
            std::getline(std::cin, userInput);

          
            if (userInput == "quit") {
                std::cout << "Exit...\n";
                break;
            }

            /
            userInput += "\r\n";

       
            if (send(m_socket, userInput.c_str(), static_cast<int>(userInput.size()), 0) == SOCKET_ERROR) {
                std::cerr << "Send failed. Error: " << WSAGetLastError() << "\n";
                break;
            }
        }

        if (bytesRecv == SOCKET_ERROR) {
            std::cerr << "Recv error: " << WSAGetLastError() << "\n";
        }

        disconnect();
    }

  
    void disconnect() {
        if (m_socket != INVALID_SOCKET) {
            closesocket(m_socket);
            auto now = std::chrono::system_clock::now();

            std::time_t currentTime = std::chrono::system_clock::to_time_t(now);


            std::tm localTime;
            localtime_s(&localTime, &currentTime);


            std::cout << "End time: "
                << std::put_time(&localTime, "%Y-%m-%d %H:%M:%S")
                << std::endl;
            m_socket = INVALID_SOCKET;
        }
    }

private:
    std::string m_serverIp;
    int m_port;
    SOCKET m_socket;
};

int main() {
    setlocale(LC_ALL, "RU");
    std::cout << "TCP CLIENT\n";

    try {
     
        TcpClient client("127.0.0.1", 666);

       
        client.connectToServer();
        client.runCommunicationLoop();
    }
    catch (const std::exception& ex) {
        std::cerr << "Critical error: " << ex.what() << "\n";
        return -1;
    }

    return 0;
}
