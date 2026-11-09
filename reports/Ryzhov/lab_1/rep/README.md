<p align="center">
Министерство образования Республики Беларусь<br>
Учреждение образования<br>
«Брестский государственный технический университет»<br>
Кафедра ИИТ
</p>

<br>

<p align="center">
Лабораторная работа №1<br>
по дисциплине «Аппаратное и программное обеспечение сетей»<br>
Тема: Организация TCP-сервера и TCP-клиента<br>
Вариант 9
</p>

<br>

<p align="right">
Выполнил:<br>
студент 3 курса<br>
группы ИИ-27<br>
Рыжов Иосиф<br>
Проверила:<br>
Кулик А.Д.
</p>

<p align="center">Брест 2026</p>

# Цель

Реализовать обмен между TCP-сервером и TCP-клиентом на C++. Научиться принимать соединение, выделять команды из потока байтов и передавать клиенту содержимое текстового файла. Реализовать отправку строки по PgDn, автоматическое подключение и журнал событий клиента.

# Задание

Для варианта 9 по таблице 1 назначены серверное задание 1 и клиентское задание 4. Реализовать TCP-эхо-сервер с командой `load <имя файла>` и клиент с вводом по PgDn. Проверить обмен собственным клиентом и текстовым TCP-клиентом, включить в отчёт листинги и пояснение основных частей.

Реализация использует C++17 и POSIX-сокеты macOS. В методичке `part3.doc` приведены WinSock и Borland C++ 5.02; в данной работе сетевые и консольные вызовы адаптированы под macOS. В Java-методичке собственный клиент выделен в лабораторную №2; в `part3.doc` сервер и клиент относятся к двум частям лабораторной №1.

# Задание 1 сервера

По команде `load <имя файла>` сервер отправляет содержимое текстового файла из каталога раздачи. Если файл отсутствует, сервер сообщает об ошибке и закрывает соединение.

В реализации успешная передача начинается со строки `OK N`, где `N` — число строк файла. Затем следуют сами строки, включая пустые. После успешной загрузки соединение остаётся открытым. Обычный текст возвращается с префиксом `Echo: `.

По умолчанию каталог раздачи совпадает с рабочим каталогом процесса сервера. Другой каталог задаётся аргументом запуска. Допускаются только имена файлов непосредственно из этого каталога; абсолютные пути, переходы в подкаталоги и символические ссылки не обслуживаются.

# Задание 4 клиента

Строка отправляется по клавише PgDn. В `client.log` записываются:

1. время начала и окончания соединения;
2. переданная серверу строка и время отправки;
3. принятая от сервера строка и время приёма.

При запуске клиент автоматически подключается к `127.0.0.1:8080`. Другой адрес и порт задаются аргументами командной строки. Специальные команды `connect`, `disconnect` и `exit` не реализованы. Локальный выход — Ctrl+D или Ctrl+C.

# Структура

```text
reports/ryzhov/lab1/
├── rep/
│   └── README.md
└── src/
    ├── server.cpp
    └── client.cpp
```

Журнал `client.log` появляется в рабочем каталоге клиента, если другой путь не задан аргументом. Сервер отдельный файл журнала не создаёт: сведения о запуске и ошибки выводятся в терминал. Исполняемые файлы и текущие журналы не являются исходниками.

# Сборка и запуск

Команды выполняются из каталога `src` на macOS:

```bash
clang++ -std=c++17 -Wall -Wextra -Wpedantic -Werror server.cpp -o server
clang++ -std=c++17 -Wall -Wextra -Wpedantic -Werror client.cpp -o client
printf 'Первая строка\nВторая строка\n' > note.txt
./server
```

Во втором окне Terminal из того же каталога:

```bash
./client
```

Клиент подключается автоматически. Набрать `load note.txt` и нажать PgDn. Для проверки ошибки затем набрать `load missing.txt` и снова нажать PgDn; такого файла в каталоге раздачи быть не должно. После ошибки сервер закроет сеанс, а клиент завершится.

PgDn распознаётся по последовательности `ESC [ 6 ~`. PgUp (`ESC [ 5 ~`) и Enter строку не отправляют. На MacBook используется Fn + стрелка вниз, если терминал передаёт это сочетание программе. Если оно прокручивает окно, следует настроить профиль терминала на отправку `\033[6~`. Приложению нужен настоящий терминал; ввод через обычный pipe не поддерживается.

Проверка сервера через netcat:

```bash
nc 127.0.0.1 8080
```

В этом сеансе строки `ping`, `load note.txt` и `load missing.txt` завершаются Enter. Netcat используется как telnet-equivalent для текстового TCP-обмена. Полная обработка управляющих переговоров Telnet в коде не реализована; фактическая проверка выполнена через `nc`.

Параметры запуска:

```text
./server [порт] [каталог]
./client [адрес] [порт] [файл_журнала]
```

Пример другого порта:

```bash
./server 9090 .
```

Во втором терминале:

```bash
./client 127.0.0.1 9090 client-9090.log
```

Сервер привязан к `127.0.0.1`; данная версия предназначена для локальной проверки на одном компьютере. Два клиента обслуживаются последовательно, поэтому перед следующим подключением нужно завершить текущий сеанс.

# Результаты проверки

Проверки выполнены 9 ноября 2026 года в среде macOS arm64, Apple Clang 21.0.0. Оба текущих исходника собраны с `-Wall -Wextra -Wpedantic -Werror` без предупреждений. Запускался настоящий C++-сервер, netcat и C++-клиент в псевдотерминале.

| Проверка | Фактический результат |
| --- | --- |
| Подключение | Получено `Hello, Student!`. |
| Строка `ping` | Получено `Echo: ping`; сеанс продолжается. |
| `load note.txt` для двухстрочного файла | Получено `OK 2` и обе строки; сеанс продолжается. |
| `load empty.txt` для пустого файла | Получено `OK 0`. |
| `load missing.txt` | Получено `ERR NOT_FOUND missing.txt`, затем соединение закрыто. |
| `load ../secret.txt` и абсолютный путь | Получено `ERR INVALID_NAME`, затем соединение закрыто. |
| Запрос символической ссылки | Ссылка не прочитана, получено `ERR NOT_FOUND`, соединение закрыто. |
| Кириллица, пустые строки и строка `OK 99` внутри файла | Текст передан корректно. |
| Enter и PgUp в псевдотерминале | До PgDn записи `SEND` не появлялись. |
| PgDn, кириллица и Backspace в псевдотерминале | Строка отправлена; в журнале присутствуют `SEND` и соответствующий `RECV`. |
| Закрытие сервера после ошибки | Клиент завершился без дополнительного ввода. |
| SIGINT | Клиент завершился, запись `DISCONNECT` появилась, настройки терминала восстановлены. |
| Недопустимый порт | Клиент завершился с ненулевым кодом. |

При проверке восстановления настроек учитывался системный флаг состояния `PENDIN`, который macOS может устанавливать при возврате к построчному вводу. Остальные параметры сравнивались с исходными.

Ниже приведён фактически сохранённый обмен через `nc` на временном порту 54025. Время захвата: 2026-11-09T12:45:28+03:00. В тестовом каталоге были подготовлены `note.txt` с двумя строками и пустой `empty.txt`.

Ввод:

```text
ping
load note.txt
load empty.txt
load ../secret.txt
```

Ответ:

```text
Hello, Student!
Echo: ping
OK 2
Первая строка
Вторая строка
OK 0
ERR INVALID_NAME
```

Фактический `client.log` отдельного сеанса на том же порту:

```text
2026-11-09T12:45:28+0300	CONNECT_ATTEMPT	127.0.0.1:54025
2026-11-09T12:45:28+0300	CONNECT	127.0.0.1:54025
2026-11-09T12:45:28+0300	RECV	Hello, Student!
2026-11-09T12:45:28+0300	SEND	load note.txt
2026-11-09T12:45:28+0300	RECV	OK 2
2026-11-09T12:45:28+0300	RECV	Первая строка
2026-11-09T12:45:28+0300	RECV	Вторая строка
2026-11-09T12:45:28+0300	SEND	load missing.txt
2026-11-09T12:45:28+0300	RECV	ERR NOT_FOUND missing.txt
2026-11-09T12:45:28+0300	REMOTE_EOF	Server closed connection
2026-11-09T12:45:28+0300	DISCONNECT	127.0.0.1:54025
```

Время записано с точностью до секунды и смещением часового пояса; поля разделены табуляцией. `SEND` фиксируется после завершения отправки строки приложением, `RECV` — после получения полной строки. Это не измерение времени доставки отдельных TCP-пакетов.

Псевдотерминал проверяет реальные процессы и настройки терминального устройства с программно переданными байтами клавиш. Физическая клавиша и настройки конкретного пользовательского Terminal, LAN и Windows не проверялись. Длительные проверки тайм-аутов и нагрузочные измерения не выполнялись. Приведённые результаты относятся к выполненным запускам при подготовке комплекта.

# Как устроена программа

## Сервер

`main` проверяет параметры, открывает каталог раздачи и создаёт сокет через `socket`. После `bind` и `listen` сервер ожидает соединение в `accept`. Дескриптор нового соединения передаётся функции `serve`, которая отправляет приветствие и обрабатывает команды.

TCP передаёт поток байтов без границ сообщений. Класс `Lines` сохраняет полученные фрагменты в буфере и выделяет строки по LF, удаляя завершающий CR. Поэтому команда может прийти несколькими чтениями, а несколько команд — одним чтением. Незавершённая строка при EOF не выполняется.

`loadFile` проверяет имя и открывает файл относительно дескриптора каталога через `openat`. Флаг `O_NOFOLLOW` запрещает открытие символической ссылки, `fstat` проверяет тип и размер. Размер файла ограничен 1 MiB, длина строки — 65536 байт. Файл читается до отправки `OK N`, чтобы сначала проверить содержимое и ограничения.

`sendLine` добавляет CRLF и повторяет `send`, пока не будут отправлены все байты: один вызов `send` может передать только часть буфера. После удачной загрузки сеанс продолжается; отсутствие файла или отказ в загрузке приводит к закрытию сеансового сокета.

Данные передаются как байты без перекодировки; для кириллицы используются UTF-8 файлы и UTF-8 терминал. Отдельного валидатора UTF-8 нет. Окончания строк нормализуются в CRLF, поэтому побайтовое совпадение исходного файла с представлением ответа не гарантируется.

## Клиент

`connectTo` разрешает адрес через `getaddrinfo` и открывает соединение через `connect`. По умолчанию адрес и порт заданы в программе, поэтому отдельная команда подключения не нужна.

Функция `exchange` использует `select`: в одном потоке ожидает либо данные сокета, либо ввод с клавиатуры. Отдельного потока чтения в этой C++-реализации нет. Полученные сетевые фрагменты накапливаются до LF; каждая выделенная строка записывается в журнал и выводится на экран. При EOF сервера цикл завершается.

`Terminal` сохраняет настройки через `tcgetattr`, отключает `ICANON` и `ECHO` и применяет режим через `tcsetattr`. Теперь ввод доступен без Enter, а локальное эхо выполняет сама программа. По `ESC [ 6 ~` текущая строка отправляется. Backspace удаляет предыдущую кодовую точку UTF-8; полноценный редактор графем и курсора не реализован.

`Journal` дописывает события в файл и сбрасывает буфер после каждой записи. Помимо обязательных `CONNECT`, `DISCONNECT`, `SEND` и `RECV`, фиксируются попытка и неудача подключения, EOF сервера и ошибки. Управляющие символы экранируются или заменяются, чтобы они не управляли терминалом при выводе.

## Завершение и ресурсы

`Descriptor`, `Terminal` и `Session` освобождают ресурсы в деструкторах. Такой подход называется RAII — Resource Acquisition Is Initialization: временем жизни ресурса управляет объект. `Descriptor` закрывает файловый дескриптор, `Terminal` восстанавливает режим ввода, `Session` записывает окончание сеанса.

Обработчики SIGINT и SIGTERM устанавливают флаг остановки. Основной цикл выходит, после чего выполняются деструкторы. SIGPIPE игнорируется, чтобы разрыв соединения обрабатывался как ошибка отправки. Принудительный `kill -9` не запускает деструкторы; после него может потребоваться команда `stty sane`.

Тайм-ауты сетевого чтения и отправки на сервере заданы по 30 секунд. Это учебный последовательный сервер без шифрования и аутентификации.

# Код программы

## server.cpp

```cpp
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
```

## client.cpp

```cpp
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
```

# Вывод

Реализованы серверное задание 1 и клиентское задание 4 варианта 9 на C++ для macOS. Сервер передаёт текст по `load`, а при отсутствии файла сообщает об ошибке и разрывает соединение. Клиент подключается автоматически, отправляет строку по PgDn и ведёт журнал начала и окончания соединения, отправленных и принятых строк с временем. Выполненные проверки подтвердили текстовый обмен, обработку ошибок, работу PgDn и восстановление режима терминала.
