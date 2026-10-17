Министерство образования Республики Беларусь
Учреждение образования
«Брестский Государственный технический университет»
Кафедра ИИТ




Лабораторная работа №1
По дисциплине «АиПОС»
Тема: «Организация UDP»



















Выполнил:
Студент 3 курса
Группы ИИ-27/24
Масюк А.Д.
Проверил: 
Кулик А.Д.






Брест 2026
# Вариант 7

# Цель работы: 

Изучить принципы работы UDP по средствам разработки двух программ, а именно, сервер и клиент.

# Задание 1:

Реализовать программно сервер. 

# Код программы:
```
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

    bool ProccedCommand(const std::string& command) {
        if (command.find("disconnect") == std::string::npos) {
            return false; 
        }

        size_t start = command.find('<');
        if (start == std::string::npos) return false;

        size_t end = command.find('>', start + 1);
        if (end == std::string::npos) return false;

        std::string adress = command.substr(start + 1, end - start - 1);

        start = command.find('<', end + 1);
        if (start == std::string::npos) return false;

        end = command.find('>', start + 1);
        if (end == std::string::npos) return false;

        std::string port = command.substr(start + 1, end - start - 1);

        if (adress == "127.0.0.1" && port == std::to_string(m_port)) {
            return true;
        }

        return false;
    }

    void runEchoLoop() {
        if (m_socket == INVALID_SOCKET) {
            std::cerr << "Сервер не запущен.\n";
            return;
        }

        logTime("Server loop started");

        std::vector<char> buffer(1024);
        sockaddr_in clientAddr{};
        int totalSize = 0;
        std::string temp;
        while (true) {
            int clientAddrSize = sizeof(clientAddr);
            int bytesRecv = recvfrom(m_socket, buffer.data(), static_cast<int>(buffer.size() - 1), 0,
                                     reinterpret_cast<sockaddr*>(&clientAddr), &clientAddrSize);
            if (bytesRecv == SOCKET_ERROR) {
                std::cerr << "Recvfrom error: " << WSAGetLastError() << "\n";
                break;
            }
            temp = std::string(buffer.data(),bytesRecv);
           while(!temp.empty()) {
                        if(temp.back() == '\n' || temp.back() == '\r' ){
                            temp.pop_back();
                        }
                        else{
                            break;
                        }
            }
            totalSize += temp.size();
            if(ProccedCommand(temp) || temp.find("~#~") != std::string::npos){
                std::string answer= "-1";
                stop();
                exit(1);
            }
            std::string answer;
            if(temp.size() == 3){
                int res = 0;
                for(const auto n: temp){
                    res += (int)n;
                }
                auto now = std::chrono::system_clock::now();
                std::time_t currentTime = std::chrono::system_clock::to_time_t(now);
                std::tm localTime;
                localtime_s(&localTime, &currentTime);
                std::cout << std::put_time(&localTime, "%Y-%m-%d %H:%M:%S") << "\tResult of sum: " << res << std::endl;
                answer = std::to_string(res) + ":" + std::to_string(totalSize);
            }
            else{
                answer = '0';
            }


            char ipStr[INET_ADDRSTRLEN];
            inet_ntop(AF_INET, &(clientAddr.sin_addr), ipStr, INET_ADDRSTRLEN);

            char hostName[NI_MAXHOST];
            if (getnameinfo(reinterpret_cast<sockaddr*>(&clientAddr), clientAddrSize,
                            hostName, NI_MAXHOST, nullptr, 0, 0) != 0) {
                std::string("Unknown host").copy(hostName, 12);
                hostName[12] = '\0';
            }

            
            if (sendto(m_socket, answer.c_str(), answer.size(), 0,
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

```
# Задание 2:

Реализовать программно клиента.

Код программы:
```
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
            std::cerr << "No active conection.\n";
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
        while(true){
            str.clear();

            std::cout << "Send to server:  ";
                std::string userInput;
                std::getline(std::cin, userInput);

               

                if(userInput == "~#~"){
                    send(m_socket, userInput.c_str(), static_cast<int>(userInput.size()), 0);
                    disconnect();
                    break;
                }
                userInput += "\r\n";
                if (send(m_socket, userInput.c_str(), static_cast<int>(userInput.size()), 0) == SOCKET_ERROR) {
                    std::cerr << "Send failed. Error: " << WSAGetLastError() << "\n";
                    disconnect();
                }
                else{
                    auto now = std::chrono::system_clock::now();
                    std::time_t currentTime = std::chrono::system_clock::to_time_t(now);
                    std::tm localTime;
                    localtime_s(&localTime, &currentTime);
                    std::cout << "Sent at: " << std::put_time(&localTime, "%Y-%m-%d %H:%M:%S") << "\tData:\t" << userInput << std::endl;
                }
                bytesRecv = recv(m_socket, buffer.data(), static_cast<int>(buffer.size() - 1), 0);
                if(bytesRecv > 0){
                    AddToStr(buffer,bytesRecv,str);
                    while(!str.empty()) {
                        if(str.back() == '\n' || str.back() == '\r' ){
                            str.pop_back();
                        }
                        else{
                            break;
                        }
                    }
                    if(str == "-1"){
                        disconnect();
                        break;
                    } 
            
                size_t delimiterPos = str.find(':');

                    if (delimiterPos != std::string::npos) {
            
                std::string partRes = str.substr(0, delimiterPos);           
                std::string partTotalSize = str.substr(delimiterPos + 1);
                std::cout << partRes << "\t" << partTotalSize << std::endl;
                std::cout << "From server got result:\t" << std::stoi(partRes) << "\tTotal send bytes:\t"<<  std::stoi(partTotalSize) << std::endl;
                }
            }
        }

        if (bytesRecv == SOCKET_ERROR) {
            std::cerr << "Recv error: " << WSAGetLastError() << "\n";
            disconnect();
            exit(1);
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

```

# Результат программы: 

## Сервер

```
PS C:\Users\Boss\Desktop\build_AIPOS_II27-Debug\src\server> ./UDP_server
UDP SERVER
UDP echo-Server started on port 666

Server loop started at: 2026-10-04 19:50:00
2026-10-04 19:50:21     Result of sum: 357
2026-10-04 19:50:53     Result of sum: 696

```


## Клиент

```
PS C:\Users\Boss\Desktop\build_AIPOS_II27-Debug\src\client> ./UDP_client 
UDP CLIENT
UDP Client initialized for target 127.0.0.1:666
Type ~#~ to quit

Start time: 2026-10-04 19:50:03
Send to server:  wwww
Sent at: 2026-10-04 19:50:05    Data:   wwww

Send to server:  www
Sent at: 2026-10-04 19:50:21    Data:   www

From server got result: 357     Total send bytes:       7
Send to server:  wwwww
Sent at: 2026-10-04 19:50:28    Data:   wwwww

Send to server:  w
Sent at: 2026-10-04 19:50:47    Data:   w

Send to server:  qqq
Sent at: 2026-10-04 19:50:53    Data:   qqq
```
# Вывод:
Изучили принцип работы TCP.
