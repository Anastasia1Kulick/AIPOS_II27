Министерство образования Республики Беларусь
Учреждение образования
«Брестский Государственный технический университет»
Кафедра ИИТ




Лабораторная работа №1
По дисциплине «АиПОС»
Тема: «Организация TCP»



















Выполнил:
Студент 3 курса
Группы ИИ-27/24
Масюк А.Д.
Проверил: 
Кулик А.Д.






Брест 2026
# Вариант 6

# Цель работы: 

Изучить принципы работы TCP по средствам разработки двух программ, а именно, сервер и клиент.

# Задание 1:

Реализовать программно сервер. 

# Код программы:
```
#include <iostream>
#include <string>
#include <atomic>
#include <vector>
#include <winsock2.h> 
#include <windows.h>
#include <ws2tcpip.h> 
#include <fstream>

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

        std::cout << "TCP Server started on port " << m_port << "\n";
        std::cout << "Waiting for connection...\n";

        run();
    }

private:
    int m_port;
    SOCKET m_listenSocket;
    std::atomic<int> m_nclients; 


    void printUsersCount() const {
        int currentClients = m_nclients.load();
        if (currentClients > 0) {
            std::cout << currentClients << " user(s) on-line\n";
        }
        else {
            std::cout << "No User on line\n";
        }
    }

   
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

          
            char host[NI_MAXHOST] = "";
            char ipStr[INET_ADDRSTRLEN] = "";

            getnameinfo(reinterpret_cast<sockaddr*>(&clientAddr), clientAddrSize, host, NI_MAXHOST, nullptr, 0, 0);
            inet_ntop(AF_INET, &(clientAddr.sin_addr), ipStr, INET_ADDRSTRLEN);

            std::cout << "+ Host: " << host << " [" << ipStr << "] new connect!\n";
            printUsersCount();

         
            ThreadParam* pParam = new ThreadParam{ this, clientSocket };

            
            HANDLE thID = CreateThread(nullptr, 0, clientThreadProxy, pParam, 0, nullptr);
            if (thID) {
                CloseHandle(thID);
            }
            else {
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

    void ProccedCommand(const std::string& command,std::string &fileName, std::string &str) {
        size_t start = command.find('<');
        if (start == std::string::npos) {
            str = " ";
        }

        size_t end = command.find('>', start + 1);
        if (end == std::string::npos) {
            str = " ";
        }

      
        str =  command.substr(start + 1, end - start - 1);

        start = end;

        start = command.find('<',start + 1);
        if (start == std::string::npos) {
            fileName = " ";
        }

        end = command.find('>', start + 1);
        if (end == std::string::npos) {
            fileName = " ";
        }

       fileName = command.substr(start + 1, end - start - 1);
    }

    bool CheckForCommand(const std::string &command) {
        std::string filename;
        std::string str;
        if (command.find("find") != std::string::npos) {
            ProccedCommand(command, filename, str);
            std::cout << "Filename: " << filename << std::endl;
            std::cout << "Str: " << str << std::endl;
            std::cout << "Find: " << FindInFile(filename, str) << std::endl;
            return true;
        }
        else {
            return false;
        }
    }

    int FindInFile(std::string filename, std::string str) {
        int numOfStr = 0;
        std::ifstream file(filename);

        
        if (!file.is_open()) {
            std::cerr << "Cant open file!" << std::endl;
            return numOfStr;
        }

        std::string line;
        while (std::getline(file, line)) {
            if (line.find(str) != std::string::npos) {
                numOfStr++;
            }
        }

       
        file.close();

        return numOfStr;
    }

    
    void handleClient(SOCKET clientSocket) {
     

        std::vector<char> buffer(20 * 1024);
        int bytesRecv = 0;


         auto SumOfChar = [](const std::vector<char>& vec, int actual_size, int &res) {
            res = 0; 
    
    
                for (int i = 0; i < actual_size; ++i) {
                    res += vec[i];
                }
            return 0;
        };

       
        std::string command;
        int totalSize = 0;
        while ((bytesRecv = recv(clientSocket, buffer.data(), static_cast<int>(buffer.size()), 0)) > 0) {
            bytesRecv -= 2;
            totalSize += bytesRecv;
            int res = 0;
            std::string temp(buffer.data(),bytesRecv);

            if(temp.find("~#~") != std::string::npos ){
                temp.clear();
                temp += "-1";
                send(clientSocket, temp.c_str(), (int)temp.size() , 0);
                closesocket(clientSocket);
                return;
            }
            SumOfChar(buffer,bytesRecv,res);
            std::string responseStr = std::to_string(res) + ':' + std::to_string(totalSize);
                if(bytesRecv  == 48){
                    responseStr += "\n";
                }
            std::cout << responseStr << std::endl;
            send(clientSocket, responseStr.c_str(), (int)responseStr.size() , 0);
  
        }

       
        m_nclients--;
        std::cout << "-disconnect\n";
        printUsersCount();

        closesocket(clientSocket);
    }
};

int main() {
    setlocale(LC_ALL, "RU");

    try {
        
        TcpServer server(666);
        server.start();
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

            if(str.back() == '\n' && !str.empty()){
                str.pop_back();

                size_t delimiterPos = str.find(':');

                if (delimiterPos != std::string::npos) {
            
                std::string partRes = str.substr(0, delimiterPos);           
                std::string partTotalSize = str.substr(delimiterPos + 1);
                std::cout << "From server got result: " << std::stoi(partRes) << "Total send bytes: "<<  std::stoi(partTotalSize) << std::endl;
                }
            }  
            else if(str == "-1"){
                disconnect();
                break;
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


```

# Результат работы:
 
## Client
```
PS C:\Users\LordMegatron\Desktop\build_AIPOS_II27-Debug\client> ./TCP_client 
TCP CLIENT
Connection with 127.0.0.1 success
Type ~#~ for quit

Start time: 2026-10-03 07:47:21
Send to server:  q
```

## Server 

```
PS C:\Users\LordMegatron\Desktop\build_AIPOS_II27-Debug\server> ./TCP_server 
TCP Server started on port 666
Waiting for connection...
+ Host: DESKTOP-3C038RU [127.0.0.1] new connect!
1 user(s) on-line
113:1
```
 

# Вывод:
Изучили принцип работы TCP.
