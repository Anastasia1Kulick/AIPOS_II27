#include <iostream>
#include <string>
#include <vector>
#include <winsock2.h>
#include <windows.h>
#include <ws2tcpip.h>
#include <sstream>
#include <algorithm>

#pragma comment(lib, "ws2_32.lib")

class UdpServer {
public:
    UdpServer(int port) : m_port(port), m_socket(INVALID_SOCKET) {
        WSADATA wsaData;
        if (WSAStartup(MAKEWORD(2, 2), &wsaData) != 0) {
            throw std::runtime_error("WSAStartup failed. Error: " + std::to_string(WSAGetLastError()));
        }
    }

    ~UdpServer() {
        if (m_socket != INVALID_SOCKET) closesocket(m_socket);
        WSACleanup();
    }

    void start() {
        // Создаем сокет для UDP (SOCK_DGRAM)
        m_socket = socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP);
        if (m_socket == INVALID_SOCKET) {
            throw std::runtime_error("Socket creation failed. Error: " + std::to_string(WSAGetLastError()));
        }

        sockaddr_in localAddr{};
        localAddr.sin_family = AF_INET;
        localAddr.sin_port = htons(m_port);
        localAddr.sin_addr.s_addr = INADDR_ANY; // Принимаем на все IP-адреса

        if (bind(m_socket, reinterpret_cast<sockaddr*>(&localAddr), sizeof(localAddr)) == SOCKET_ERROR) {
            throw std::runtime_error("Bind failed. Error: " + std::to_string(WSAGetLastError()));
        }

        std::cout << "UDP Server (Variant 13) started on port " << m_port << "\n";
        std::cout << "Waiting for datagrams...\n";

        run();
    }

private:
    int m_port;
    SOCKET m_socket;

    // Функция для разворота слов в предложении
    std::string reverseWords(const std::string& sentence) {
        std::istringstream iss(sentence);
        std::vector<std::string> words;
        std::string word;
        while (iss >> word) words.push_back(word);
        
        std::reverse(words.begin(), words.end());
        
        std::string result;
        for (size_t i = 0; i < words.size(); ++i) {
            result += words[i];
            if (i < words.size() - 1) result += " ";
        }
        return result;
    }

    void run() {
        char recvBuf[2048];
        sockaddr_in clientAddr{};
        int clientAddrSize = sizeof(clientAddr);

        while (true) {
            // В отличие от TCP, здесь мы используем recvfrom. 
            // Он сразу возвращает данные и адрес отправителя.
            int bytesRecv = recvfrom(m_socket, recvBuf, sizeof(recvBuf) - 1, 0,
                                     reinterpret_cast<sockaddr*>(&clientAddr), &clientAddrSize);
            
            if (bytesRecv == SOCKET_ERROR) {
                std::cerr << "recvfrom failed. Error: " << WSAGetLastError() << "\n";
                continue;
            }

            recvBuf[bytesRecv] = '\0';
            std::string message(recvBuf);
            
            // Очищаем от возможных переносов строк
            message.erase(std::remove(message.begin(), message.end(), '\r'), message.end());
            message.erase(std::remove(message.begin(), message.end(), '\n'), message.end());

            char ipStr[INET_ADDRSTRLEN] = "";
            inet_ntop(AF_INET, &(clientAddr.sin_addr), ipStr, INET_ADDRSTRLEN);
            std::cout << "\n[From " << ipStr << "] Datagram received: [" << message << "]\n";

            // Проверка на завершение сеанса (две точки подряд)
            if (message.find("..") != std::string::npos) {
                std::string endMsg = "Session ended by double dot.";
                std::cout << "[-] Client requested disconnect.\n";
                sendto(m_socket, endMsg.c_str(), (int)endMsg.size(), 0,
                       reinterpret_cast<sockaddr*>(&clientAddr), clientAddrSize);
                continue; // UDP не закрывает соединение, просто продолжаем слушать
            }

            // Если есть точка, обрабатываем предложение
            if (message.find('.') != std::string::npos) {
                std::string sentence = message.substr(0, message.find('.'));
                if (!sentence.empty()) {
                    std::string reversed = reverseWords(sentence);
                    std::cout << "Sent response: [" << reversed << "]\n";
                    // Отправляем ответ по адресу клиента, от которого пришла датаграмма
                    sendto(m_socket, reversed.c_str(), (int)reversed.size(), 0,
                           reinterpret_cast<sockaddr*>(&clientAddr), clientAddrSize);
                }
            } else {
                std::cout << "No dot at the end, ignoring.\n";
            }
        }
    }
};

int main() {
    setlocale(LC_ALL, "RU");
    try {
        UdpServer server(666);
        server.start();
    } catch (const std::exception& ex) {
        std::cerr << "Critical error: " << ex.what() << "\n";
        return -1;
    }
    return 0;
}