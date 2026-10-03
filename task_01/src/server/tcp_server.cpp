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
