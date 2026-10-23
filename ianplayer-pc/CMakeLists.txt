cmake_minimum_required(VERSION 3.24)

project(IanPlayerPC VERSION 0.1.0 LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_AUTOMOC ON)

find_package(Qt6 REQUIRED COMPONENTS Quick QuickControls2 Core Sql)

qt_policy(SET QTP0001 NEW)
qt_policy(SET QTP0004 NEW)

qt_standard_project_setup()

set(MPV_DIR ${CMAKE_SOURCE_DIR}/third_party/mpv)

# ---- ianplayer_core: logic non-UI (network/cache) supaya bisa di-test gtest ----
add_library(ianplayer_core STATIC
    src/net/stream_url_resolver.cpp
)
target_include_directories(ianplayer_core PUBLIC ${CMAKE_CURRENT_SOURCE_DIR}/src)
target_link_libraries(ianplayer_core PUBLIC Qt6::Core Qt6::Sql)

# ---- app exe ----
qt_add_executable(ianplayer
    src/main.cpp
    src/playback/playback_controller.h
    src/playback/playback_controller.cpp
    assets/app.manifest
)

target_include_directories(ianplayer PRIVATE ${MPV_DIR}/include)
target_link_libraries(ianplayer PRIVATE
    Qt6::Quick
    Qt6::QuickControls2
    ianplayer_core
    ${MPV_DIR}/lib/x86_64/mpv.lib
)

qt_add_qml_module(ianplayer
    URI IanPlayer.PC
    VERSION 1.0
    QML_FILES
        src/ui/AppWindow.qml
        src/ui/Theme.qml
)

if (MSVC)
    target_compile_options(ianplayer PRIVATE /W4 /permissive- /utf-8)
    target_compile_options(ianplayer_core PRIVATE /W4 /permissive- /utf-8)
endif()

# ---- tests (gtest) ----
find_package(GTest CONFIG REQUIRED)

add_executable(ianplayer_tests
    src/tests/stream_url_test.cpp
)
target_link_libraries(ianplayer_tests PRIVATE
    ianplayer_core
    GTest::gtest
    GTest::gtest_main
)
if (MSVC)
    target_compile_options(ianplayer_tests PRIVATE /W4 /permissive- /utf-8)
endif()

enable_testing()
add_test(NAME stream_url COMMAND ianplayer_tests)

# ---- POST_BUILD deploy: DLL libmpv + yt-dlp di samping exe ----
add_custom_command(TARGET ianplayer POST_BUILD
    COMMAND ${CMAKE_COMMAND} -E copy_if_different
        ${MPV_DIR}/lib/x86_64/libmpv-2.dll
        $<TARGET_FILE_DIR:ianplayer>
    COMMAND ${CMAKE_COMMAND} -E copy_if_different
        ${CMAKE_SOURCE_DIR}/third_party/ytdlp/yt-dlp.exe
        $<TARGET_FILE_DIR:ianplayer>
    COMMAND ${CMAKE_COMMAND} -E copy_if_different
        ${MPV_DIR}/lib/x86_64/libmpv-2.dll
        $<TARGET_FILE_DIR:ianplayer_tests>
)
