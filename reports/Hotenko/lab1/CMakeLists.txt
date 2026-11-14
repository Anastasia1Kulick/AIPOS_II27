cmake_minimum_required(VERSION 3.24)

project(lab1_tcp LANGUAGES CXX)

# Подключаем папки с клиентом и сервером
add_subdirectory(src/client)
add_subdirectory(src/server)