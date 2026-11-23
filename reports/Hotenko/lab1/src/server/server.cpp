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