cmake_minimum_required(VERSION 3.24)

project(IanPlayerPC VERSION 0.1.0 LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_AUTOMOC ON)

find_package(Qt6 REQUIRED COMPONENTS Quick QuickControls2)

qt_policy(SET QTP0001 NEW)
qt_policy(SET QTP0004 NEW)

qt_standard_project_setup()

set(MPV_DIR ${CMAKE_SOURCE_DIR}/third_party/mpv)

qt_add_executable(ianplayer
    src/main.cpp
    assets/app.manifest
)

target_include_directories(ianplayer PRIVATE ${MPV_DIR}/include)
target_link_libraries(ianplayer PRIVATE
    Qt6::Quick
    Qt6::QuickControls2
    ${MPV_DIR}/lib/x86_64/mpv.lib
)

qt_add_qml_module(ianplayer
    URI IanPlayer.PC
    VERSION 1.0
    QML_FILES
        src/ui/AppWindow.qml
        src/ui/Theme.qml
)

# Flags konsisten (MSVC)
if (MSVC)
    target_compile_options(ianplayer PRIVATE /W4 /permissive- /utf-8)
endif()

# mpv-2.dll harus nempel di samping exe tiap build
add_custom_command(TARGET ianplayer POST_BUILD
    COMMAND ${CMAKE_COMMAND} -E copy_if_different
        ${MPV_DIR}/lib/x86_64/libmpv-2.dll
        $<TARGET_FILE_DIR:ianplayer>)
