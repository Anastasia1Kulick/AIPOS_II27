  #include <arpa/inet.h>
  #include <sys/socket.h>
  #include <sys/stat.h>
  #include <unistd.h>
  #include <fcntl.h>
  #include <cerrno>
  #include <csignal>
  #include <cstring>
  #include <iostream>
  #include <sstream>
  #include <stdexcept>
  #include <string>
  #include <vector>

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

  class Lines {
      int fd;
      std::string buffer;
  public:
      explicit Lines(int socket) : fd(socket) {}
      bool next(std::string& line) {
          while (!stopped) {
              auto end = buffer.find('\n');
              if (end != std::string::npos) {
                  line = buffer.substr(0, end);
                  buffer.erase(0, end + 1);
                  if (!line.empty() && line.back() == '\r') line.pop_back();
                  if (line.size() > 65536) throw std::runtime_error("Line too long");
                  return true;
              }
              if (buffer.size() > 65537) throw std::runtime_error("Line too long");
              char bytes[4096];
              ssize_t count = ::recv(fd, bytes, sizeof(bytes), 0);
              if (count < 0 && errno == EINTR) continue;
              if (count < 0) throw std::runtime_error(std::strerror(errno));
              if (count == 0) {
                  if (!buffer.empty()) throw std::runtime_error("Incomplete line");
                  return false;
              }
              buffer.append(bytes, static_cast<std::size_t>(count));
          }
          return false;
      }
  };

  bool loadFile(int socket, int directory, const std::string& name) {
      if (name.empty() || name == "." || name == ".." ||
          name.find_first_of("/\\\r\n") != std::string::npos ||
          name.find('\0') != std::string::npos) {
          sendLine(socket, "ERR INVALID_NAME");
          return false;
      }
      int raw = ::openat(directory, name.c_str(), O_RDONLY | O_NOFOLLOW | O_NONBLOCK);
      if (raw < 0) {
          sendLine(socket, "ERR NOT_FOUND " + name);
          return false;
      }
      Descriptor file(raw);
      struct stat info {};
      if (::fstat(file.fd, &info) != 0 || !S_ISREG(info.st_mode) || info.st_size > 1048576) {
          sendLine(socket, "ERR FILE " + name);
          return false;
      }
      std::string text;
      char bytes[4096];
      while (!stopped) {
          ssize_t count = ::read(file.fd, bytes, sizeof(bytes));
          if (count < 0 && errno == EINTR) continue;
          if (count < 0) {
              sendLine(socket, "ERR FILE " + name);
              return false;
          }
          if (count == 0) break;
          text.append(bytes, static_cast<std::size_t>(count));
          if (text.size() > 1048576) {
              sendLine(socket, "ERR FILE " + name);
              return false;
          }
      }
      if (stopped) return false;
      std::istringstream input(text);
      std::vector<std::string> lines;
      std::string line;
      while (std::getline(input, line)) {
          if (!line.empty() && line.back() == '\r') line.pop_back();
          if (line.size() > 65536) {
              sendLine(socket, "ERR FILE " + name);
              return false;
          }
          lines.push_back(line);
      }
      sendLine(socket, "OK " + std::to_string(lines.size()));
      for (const auto& item : lines) sendLine(socket, item);
      return true;
  }

  void serve(int socket, int directory) {
      sendLine(socket, "Hello, Student!");
      Lines input(socket);
      std::string command;
      while (input.next(command)) {
          if (command == "load") {
              sendLine(socket, "ERR SYNTAX load filename");
          } else if (command.rfind("load ", 0) == 0) {
              if (!loadFile(socket, directory, command.substr(5))) return;
          } else if (command.size() > 65530) {
              sendLine(socket, "ERR LINE_TOO_LONG");
          } else {
              sendLine(socket, "Echo: " + command);
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
          if (argc > 3) throw std::runtime_error("Usage: server [port] [directory]");
          int port = argc > 1 ? parsePort(argv[1]) : 8080;
          Descriptor directory(::open(argc > 2 ? argv[2] : ".", O_RDONLY | O_DIRECTORY));
          Descriptor server(::socket(AF_INET, SOCK_STREAM, 0));
          int reuse = 1;
          if (::setsockopt(server.fd, SOL_SOCKET, SO_REUSEADDR, &reuse, sizeof(reuse)) < 0)
              throw std::runtime_error(std::strerror(errno));
          sockaddr_in address {};
          address.sin_family = AF_INET;
          address.sin_port = htons(static_cast<unsigned short>(port));
          address.sin_addr.s_addr = htonl(INADDR_LOOPBACK);
          if (::bind(server.fd, reinterpret_cast<sockaddr*>(&address), sizeof(address)) < 0 ||
              ::listen(server.fd, 16) < 0)
              throw std::runtime_error(std::strerror(errno));
          std::cout << "LISTEN 127.0.0.1:" << port << std::endl;
          while (!stopped) {
              int raw = ::accept(server.fd, nullptr, nullptr);
              if (raw < 0 && errno == EINTR) continue;
              Descriptor client(raw);
              timeval timeout {30, 0};
              ::setsockopt(client.fd, SOL_SOCKET, SO_RCVTIMEO, &timeout, sizeof(timeout));
              ::setsockopt(client.fd, SOL_SOCKET, SO_SNDTIMEO, &timeout, sizeof(timeout));
              try {
                  serve(client.fd, directory.fd);
              } catch (const std::exception& error) {
                  if (!stopped) std::cerr << error.what() << '\n';
              }
          }
      } catch (const std::exception& error) {
          std::cerr << error.what() << '\n';
          return 1;
      }
      return 0;
  }
