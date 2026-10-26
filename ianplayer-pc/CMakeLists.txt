cmake_minimum_required(VERSION 3.24)

project(IanPlayerPC VERSION 0.1.0 LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_AUTOMOC ON)

find_package(Qt6 REQUIRED COMPONENTS Quick QuickControls2 Core Sql Concurrent Gui)
find_package(TagLib CONFIG REQUIRED)

qt_policy(SET QTP0001 NEW)
qt_policy(SET QTP0004 NEW)

qt_standard_project_setup()

set(MPV_DIR ${CMAKE_SOURCE_DIR}/third_party/mpv)

# ---- ianplayer_core: logic non-UI (network/cache) supaya bisa di-test gtest ----
add_library(ianplayer_core STATIC
    src/net/stream_url_resolver.cpp
    src/net/innertube_client.cpp
    src/net/innertube_client.h
    src/net/tidal_repository.cpp
    src/net/tidal_repository.h
    src/net/lyric_repository.cpp
    src/net/lyric_repository.h
    src/net/thumbnail_cache.cpp
    src/net/thumbnail_cache.h
    src/core/title_cleaner.cpp
    src/core/title_cleaner.h
    src/playback/queue_manager.h
    src/playback/queue_manager.cpp
)
target_include_directories(ianplayer_core PUBLIC ${CMAKE_CURRENT_SOURCE_DIR}/src)
target_link_libraries(ianplayer_core PUBLIC Qt6::Core Qt6::Sql Qt6::Network)

# ---- app exe ----
qt_add_executable(ianplayer
    src/main.cpp
    src/playback/playback_controller.h
    src/playback/playback_controller.cpp
    src/core/library_scanner.h
    src/core/library_scanner.cpp
    src/db/library_database.h
    src/db/library_database.cpp
    src/app/library_service.h
    src/app/library_service.cpp
    src/models/library_song_model.h
    src/models/library_song_model.cpp
    src/models/library_proxy_model.h
    src/models/library_proxy_model.cpp
    src/models/album_list_model.h
    src/models/album_list_model.cpp
    src/models/artist_list_model.h
    src/models/artist_list_model.cpp
    src/models/playlist_list_model.h
    src/models/playlist_list_model.cpp
    src/models/playlist_song_model.h
    src/models/playlist_song_model.cpp
    src/models/stream_song_model.h
    src/models/stream_song_model.cpp
    src/app/stream_service.h
    src/app/stream_service.cpp
    src/app/lyric_service.h
    src/app/lyric_service.cpp
    src/platform/smtc_controller.h
    src/platform/smtc_controller.cpp
    assets/app.manifest
)

qt_add_resources(ianplayer "app_fonts"
    PREFIX "/"
    FILES
        src/resources/fonts/Inter-Regular.ttf
        src/resources/fonts/Inter-Medium.ttf
        src/resources/fonts/Inter-SemiBold.ttf
        src/resources/fonts/Inter-Bold.ttf
)

target_include_directories(ianplayer PRIVATE ${MPV_DIR}/include)
target_link_libraries(ianplayer PRIVATE
    Qt6::Quick
    Qt6::QuickControls2
    Qt6::Concurrent
    TagLib::TagLib
    ianplayer_core
    runtimeobject
    ${MPV_DIR}/lib/x86_64/mpv.lib
)

target_include_directories(ianplayer PRIVATE
    "C:/Program Files (x86)/Windows Kits/10/Include/10.0.26100.0/cppwinrt"
)

# Theme = QML singleton: generator qmldir cuma baca property QT_QML_SINGLETON_TYPE
# ini (pragma Singleton di file tidak di-scan otomatis oleh qt 6.8 macros).
set_source_files_properties(src/ui/Theme.qml PROPERTIES QT_QML_SINGLETON_TYPE TRUE)

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
    src/tests/filename_parse_test.cpp
    src/tests/title_clean_test.cpp
    src/tests/library_db_test.cpp
    src/tests/library_model_test.cpp
    src/tests/queue_manager_test.cpp
    src/tests/innertube_test.cpp
    src/tests/test_main.cpp
    src/core/library_scanner.h
    src/core/library_scanner.cpp
    src/db/library_database.h
    src/db/library_database.cpp
    src/models/library_song_model.h
    src/models/library_song_model.cpp
    src/models/library_proxy_model.h
    src/models/library_proxy_model.cpp
)
target_link_libraries(ianplayer_tests PRIVATE
    ianplayer_core
    TagLib::TagLib
    Qt6::Gui
    GTest::gtest
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
