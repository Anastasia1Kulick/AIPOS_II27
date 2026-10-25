cmake_minimum_required(VERSION 3.10)


project(TCP_client CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_EXPORT_COMPILE_COMMANDS ON)


if(CMAKE_CXX_COMPILER_ID STREQUAL "GNU" OR CMAKE_CXX_COMPILER_ID STREQUAL "Clang")
    add_compile_options(-finput-charset=UTF-8 -fexec-charset=UTF-8)
endif()


add_executable(TCP_client ./tcp_client.cpp)

if(CMAKE_CXX_COMPILER_ID STREQUAL "GNU")
    target_link_options(TCP_client PRIVATE -static-libgcc -static-libstdc++ -static)
endif()


target_link_libraries(TCP_client ws2_32)
