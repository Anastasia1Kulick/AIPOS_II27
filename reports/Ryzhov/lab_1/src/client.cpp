#include <arpa/inet.h>
#include <netdb.h>
#include <sys/select.h>
#include <sys/socket.h>
#include <termios.h>
#include <unistd.h>
#include <cerrno>
#include <chrono>
#include <csignal>
#include <cstring>
#include <ctime>
#include <fstream>
#include <iomanip>
#include <iostream>
#include <sstream>
#include <stdexcept>
#include <string>

volatile std::sig_atomic_t stopped = 0;

void stop(int) { stopped = 1; }

struct Descriptor {
    int fd;
    explicit Descriptor(int value) : fd(value) {
        if (fd < 0) throw std::runtime_error(std::strerror(errno));
    }
    ~Descriptor() { ::close(fd); }
    Descriptor(const Descriptor&) = delete;
    Descriptor& operator=(const Descriptor&) = delete;
};

int parsePort(const std::string& value) {
    std::size_t used = 0;
    int port = std::stoi(value, &used);
    if (used != value.size() || port < 1 || port > 65535)
        throw std::runtime_error("Invalid port");
    return port;
}

std::string escaped(const std::string& text) {
    std::string result;
    for (unsigned char c : text) {
        if (c == '\\') result += "\\\\";
        else if (c == '\t') result += "\\t";
        else if (c == '\n') result += "\\n";
        else if (c == '\r') result += "\\r";
        else if (c < 32 || c == 127) result += '?';
        else result += static_cast<char>(c);
    }
    return result;
}

class Journal {
    std::ofstream file;
public:
    explicit Journal(const std::string& name) : file(name, std::ios::app) {
        if (!file) throw std::runtime_error("Cannot open log: " + name);
    }
    void write(const std::string& event, const std::string& text) {
        auto now = std::chrono::system_clock::now();
        std::time_t time = std::chrono::system_clock::to_time_t(now);
        std::tm local {};
        localtime_r(&time, &local);
        file << std::put_time(&local, "%Y-%m-%dT%H:%M:%S%z")
             << '\t' << event << '\t' << escaped(text) << '\n';
        file.flush();
        if (!file) throw std::runtime_error("Log write failed");
    }
};

struct Session {
    Journal& journal;
    std::string address;
    ~Session() {
        try { journal.write("DISCONNECT", address); }
        catch (const std::exception& error) { std::cerr << error.what() << '\n'; }
    }
};

class Terminal {
    termios saved {};
public:
    Terminal() {
        if (::tcgetattr(STDIN_FILENO, &saved) < 0)
            throw std::runtime_error("Run client in a terminal");
        termios mode = saved;
        mode.c_lflag &= static_cast<tcflag_t>(~(ICANON | ECHO));
        mode.c_cc[VMIN] = 1;
        mode.c_cc[VTIME] = 0;
        if (::tcsetattr(STDIN_FILENO, TCSANOW, &mode) < 0)
            throw std::runtime_error(std::strerror(errno));
    }
    ~Terminal() {
        while (::tcsetattr(STDIN_FILENO, TCSANOW, &saved) < 0) {
            if (errno != EINTR) break;
        }
    }
    Terminal(const Terminal&) = delete;
    Terminal& operator=(const Terminal&) = delete;
};

void sendLine(int fd, const std::string& line) {
    std::string data = line + "\r\n";
    std::size_t sent = 0;
    while (sent < data.size()) {
        ssize_t count = ::send(fd, data.data() + sent, data.size() - sent, 0);
        if (count < 0 && errno == EINTR && !stopped) continue;
        if (count <= 0) throw std::runtime_error("Send failed");
        sent += static_cast<std::size_t>(count);
    }
}

int connectTo(const std::string& host, int port) {
    addrinfo hints {};
    hints.ai_family = AF_UNSPEC;
    hints.ai_socktype = SOCK_STREAM;
    addrinfo* addresses = nullptr;
    int status = ::getaddrinfo(host.c_str(), std::to_string(port).c_str(), &hints, &addresses);
    if (status != 0) throw std::runtime_error(gai_strerror(status));
    int connected = -1;
    for (auto* entry = addresses; entry && !stopped; entry = entry->ai_next) {
        int fd = ::socket(entry->ai_family, entry->ai_socktype, entry->ai_protocol);
        if (fd < 0) continue;
        if (::connect(fd, entry->ai_addr, entry->ai_addrlen) == 0) {
            connected = fd;
            break;
        }
        ::close(fd);
    }
    ::freeaddrinfo(addresses);
    if (connected < 0) throw std::runtime_error("Connection failed");
    return connected;
}

void exchange(int socket, Journal& journal) {
    if (socket >= FD_SETSIZE) throw std::runtime_error("Socket descriptor too large");
    Terminal terminal;
    std::string input, incoming, escape;
    auto escapeTime = std::chrono::steady_clock::now();
    std::cout << "PgDn: send | Ctrl+D: exit\n" << std::flush;
    while (!stopped) {
        if (!escape.empty() && std::chrono::steady_clock::now() - escapeTime >
            std::chrono::milliseconds(500)) escape.clear();
        fd_set ready;
        FD_ZERO(&ready);
        FD_SET(STDIN_FILENO, &ready);
        FD_SET(socket, &ready);
        timeval timeout {0, 100000};
        int count = ::select(socket + 1, &ready, nullptr, nullptr, &timeout);
        if (count < 0 && errno == EINTR) continue;
        if (count < 0) throw std::runtime_error(std::strerror(errno));
        if (FD_ISSET(socket, &ready)) {
            char bytes[4096];
            ssize_t received = ::recv(socket, bytes, sizeof(bytes), 0);
            if (received < 0 && errno == EINTR) continue;
            if (received < 0) throw std::runtime_error(std::strerror(errno));
            if (received == 0) {
                if (!incoming.empty()) throw std::runtime_error("Incomplete response");
                journal.write("REMOTE_EOF", "Server closed connection");
                return;
            }
            incoming.append(bytes, static_cast<std::size_t>(received));
            std::size_t end;
            while ((end = incoming.find('\n')) != std::string::npos) {
                std::string line = incoming.substr(0, end);
                incoming.erase(0, end + 1);
                if (!line.empty() && line.back() == '\r') line.pop_back();
                if (line.size() > 65536) throw std::runtime_error("Response too long");
                journal.write("RECV", line);
                std::cout << "\n[server] " << escaped(line) << '\n' << std::flush;
            }
            if (incoming.size() > 65537) throw std::runtime_error("Response too long");
        }
        if (FD_ISSET(STDIN_FILENO, &ready)) {
            unsigned char c = 0;
            ssize_t received = ::read(STDIN_FILENO, &c, 1);
            if (received < 0 && errno == EINTR) continue;
            if (received < 0) throw std::runtime_error(std::strerror(errno));
            if (received == 0 || c == 4) return;
            if (!escape.empty()) {
                escape += static_cast<char>(c);
                if (escape.size() == 2 && c != '[') escape.clear();
                else if (escape.size() > 2 && c >= 0x40 && c <= 0x7e) {
                    bool send = escape == "\x1b[6~";
                    escape.clear();
                    if (send) {
                        sendLine(socket, input);
                        journal.write("SEND", input);
                        input.clear();
                        std::cout << '\n' << std::flush;
                    }
                } else if (escape.size() > 24) escape.clear();
            } else if (c == 27) {
                escape = "\x1b";
                escapeTime = std::chrono::steady_clock::now();
            } else if (c == 127 || c == 8) {
                if (!input.empty()) {
                    std::size_t start = input.size() - 1;
                    while (start > 0 && (static_cast<unsigned char>(input[start]) & 0xc0) == 0x80)
                        --start;
                    input.erase(start);
                    std::cout << "\b \b" << std::flush;
                }
            } else if (c >= 32) {
                if (input.size() >= 65536) throw std::runtime_error("Input too long");
                input += static_cast<char>(c);
                std::cout << static_cast<char>(c) << std::flush;
            }
        }
    }
}

int main(int argc, char* argv[]) {
    std::signal(SIGPIPE, SIG_IGN);
    struct sigaction action {};
    action.sa_handler = stop;
    sigemptyset(&action.sa_mask);
    sigaction(SIGINT, &action, nullptr);
    sigaction(SIGTERM, &action, nullptr);
    try {
        if (argc > 4) throw std::runtime_error("Usage: client [host] [port] [log]");
        std::string host = argc > 1 ? argv[1] : "127.0.0.1";
        int port = argc > 2 ? parsePort(argv[2]) : 8080;
        Journal journal(argc > 3 ? argv[3] : "client.log");
        std::string address = host + ":" + std::to_string(port);
        journal.write("CONNECT_ATTEMPT", address);
        int raw;
        try { raw = connectTo(host, port); }
        catch (const std::exception& error) {
            journal.write("CONNECT_FAILED", error.what());
            throw;
        }
        Descriptor socket(raw);
        journal.write("CONNECT", address);
        Session session {journal, address};
        try { exchange(socket.fd, journal); }
        catch (const std::exception& error) {
            journal.write("ERROR", error.what());
            throw;
        }
    } catch (const std::exception& error) {
        std::cerr << error.what() << '\n';
        return 1;
    }
    return 0;
}
