#include <iostream>
#include <string>
#include <map>
#include <cstring>
#include <arpa/inet.h>
#include <sys/socket.h>
#include <sys/select.h>
#include <unistd.h>

using namespace std;

const int PORT = 666;
const int BUFFER_SIZE = 1024;

int main() {
    int serverSocket = socket(AF_INET, SOCK_DGRAM, 0);

    sockaddr_in serverAddr{};
    serverAddr.sin_family = AF_INET;
    serverAddr.sin_addr.s_addr = INADDR_ANY;
    serverAddr.sin_port = htons(PORT);

    bind(serverSocket,
         reinterpret_cast<sockaddr*>(&serverAddr),
         sizeof(serverAddr));

    cout << "UDP server started on port " << PORT << endl;

    while (true) {
        fd_set readSet;
        FD_ZERO(&readSet);

        FD_SET(serverSocket, &readSet);
        FD_SET(STDIN_FILENO, &readSet);

        int maxFd = serverSocket;

        select(maxFd + 1, &readSet, nullptr, nullptr, nullptr);

        if (FD_ISSET(serverSocket, &readSet)) {
            char buffer[BUFFER_SIZE];

            sockaddr_in clientAddr{};
            socklen_t clientAddrLen = sizeof(clientAddr);

            ssize_t bytesReceived = recvfrom(
                serverSocket,
                buffer,
                BUFFER_SIZE - 1,
                0,
                reinterpret_cast<sockaddr*>(&clientAddr),
                &clientAddrLen
            );

            if (bytesReceived <= 0)
                continue;

            buffer[bytesReceived] = '\0';

            string data(buffer, bytesReceived);

            map<char, int> frequencies;

            for (char c : data)
                frequencies[c]++;

            if (frequencies.size() < 3) {
                string message =
                    "Number of different characters is less than 3. Disconnect.";

                sendto(
                    serverSocket,
                    message.c_str(),
                    message.size(),
                    0,
                    reinterpret_cast<sockaddr*>(&clientAddr),
                    clientAddrLen
                );

                continue;
            }

            string response = "<";
            bool first = true;

            for (const auto& item : frequencies) {
                if (!first)
                    response += ", ";

                response += item.first;
                response += "-";
                response += to_string(item.second);

                first = false;
            }

            response += ">";

            sendto(
                serverSocket,
                response.c_str(),
                response.size(),
                0,
                reinterpret_cast<sockaddr*>(&clientAddr),
                clientAddrLen
            );
        }

        if (FD_ISSET(STDIN_FILENO, &readSet)) {
            string command;
            getline(cin, command);

            string keyword;
            string ip;
            int port;

            size_t firstSpace = command.find(' ');
            size_t secondSpace = command.find(' ', firstSpace + 1);

            if (firstSpace != string::npos &&
                secondSpace != string::npos) {

                keyword = command.substr(0, firstSpace);
                ip = command.substr(
                    firstSpace + 1,
                    secondSpace - firstSpace - 1
                );

                port = stoi(command.substr(secondSpace + 1));

                if (keyword == "disconnect") {
                    sockaddr_in clientAddr{};

                    clientAddr.sin_family = AF_INET;
                    clientAddr.sin_port = htons(port);
                    inet_pton(
                        AF_INET,
                        ip.c_str(),
                        &clientAddr.sin_addr
                    );

                    string message = "Disconnected by server.";

                    sendto(
                        serverSocket,
                        message.c_str(),
                        message.size(),
                        0,
                        reinterpret_cast<sockaddr*>(&clientAddr),
                        sizeof(clientAddr)
                    );
                }
            }
        }
    }

    close(serverSocket);}