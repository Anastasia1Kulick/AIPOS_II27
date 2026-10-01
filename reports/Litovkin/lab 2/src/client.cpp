#include <iostream>
#include <fstream>
#include <string>
#include <ctime>
#include <arpa/inet.h>
#include <sys/socket.h>
#include <unistd.h>
#include <termios.h>

using namespace std;

const char* SERVER_IP = "127.0.0.1";
const int SERVER_PORT = 666;
const int BUFFER_SIZE = 1024;

string getTime() {
    time_t now = time(nullptr);

    char buffer[64];

    strftime(
        buffer,
        sizeof(buffer),
        "%Y-%m-%d %H:%M:%S",
        localtime(&now)
    );

    return buffer;
}

int main() {
    int clientSocket = socket(AF_INET, SOCK_DGRAM, 0);

    sockaddr_in serverAddr{};
    serverAddr.sin_family = AF_INET;
    serverAddr.sin_port = htons(SERVER_PORT);

    inet_pton(
        AF_INET,
        SERVER_IP,
        &serverAddr.sin_addr
    );

    ofstream logFile("client.log", ios::app);

    logFile << "Start: " << getTime() << endl;

    termios oldTermios;
    termios newTermios;

    tcgetattr(STDIN_FILENO, &oldTermios);

    newTermios = oldTermios;
    newTermios.c_lflag &= ~(ICANON | ECHO);

    tcsetattr(
        STDIN_FILENO,
        TCSANOW,
        &newTermios
    );

    string input;

    cout << "Enter text. Press Home to send:" << endl;

    while (true) {
        char c;
        read(STDIN_FILENO, &c, 1);

        if (c == 27) {
            char c2;
            char c3;

            read(STDIN_FILENO, &c2, 1);
            read(STDIN_FILENO, &c3, 1);

            if ((c2 == '[' || c2 == 'O') && c3 == 'H') {
                cout << endl;

                sendto(
                    clientSocket,
                    input.c_str(),
                    input.size(),
                    0,
                    reinterpret_cast<sockaddr*>(&serverAddr),
                    sizeof(serverAddr)
                );

                logFile
                    << "Sent: "
                    << input
                    << " | "
                    << getTime()
                    << endl;

                char buffer[BUFFER_SIZE];

                sockaddr_in senderAddr{};
                socklen_t senderAddrLen = sizeof(senderAddr);

                ssize_t bytesReceived = recvfrom(
                    clientSocket,
                    buffer,
                    BUFFER_SIZE - 1,
                    0,
                    reinterpret_cast<sockaddr*>(&senderAddr),
                    &senderAddrLen
                );

                buffer[bytesReceived] = '\0';

                string response(buffer);

                cout << "Server: " << response << endl;

                if (response.find("Disconnect") != string::npos ||
                    response.find("Disconnected") != string::npos) {
                    break;
                }

                input.clear();

                cout << "Enter text. Press Home to send:" << endl;
            }

            continue;
        }

        if (c == 127 && !input.empty()) {
            input.pop_back();

            cout << "\b \b" << flush;

            continue;
        }

        input += c;

        cout << c << flush;
    }

    tcsetattr(
        STDIN_FILENO,
        TCSANOW,
        &oldTermios
    );

    logFile << "End: " << getTime() << endl;

    logFile.close();

    close(clientSocket);}