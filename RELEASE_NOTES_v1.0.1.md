# 🎵 IanPlayer v1.0.1 - Bug Fixes & Feature Improvements

## What's New

This release focuses on fixing critical bugs and improving the overall user experience with enhanced playlist management and UI polish.

---

## ✨ New Features

### ✏️ Playlist Editing
- **Edit Playlist Names**: Click the pencil icon on any playlist card to rename it
- **Custom Playlist Images**: Support for custom playlist cover images (prepared for future image picker)
- **Delete Playlists**: Remove playlists with a dedicated delete button

### 📋 Album Display
- **Proper Album Art**: Albums section now displays actual album art from your music files
- **Correct Album Grouping**: Albums are now grouped by album name instead of artist name
- **Album Information**: Shows album name and song count for each album

---

## 🐛 Bug Fixes

### Playlist Management
- **Fixed Add Songs to Playlist**: Songs now appear immediately after adding them to a playlist (no need to leave and re-enter)
- **Fixed Drag & Reorder**: Smooth drag-and-drop reordering of songs within playlists with proper visual feedback and elevation animation
- **Persistent Changes**: Playlist modifications are now properly saved and persist across app restarts

### UI/UX Improvements
- **Rounded Checkboxes**: All checkboxes in dialogs now have rounded corners (6dp radius) for a more polished look
- **Auto-Hide Scrollbar**: Scrollbar in playlist view automatically fades out after 1 second of inactivity and reappears when scrolling
- **Better Visual Feedback**: Improved drag handle styling with circular background and accent color
- **Reactive UI**: Interface now updates immediately when playlist changes are made

---

## 🔧 Technical Improvements

### Data Model Enhancements
- Added `imageUri` field to Playlist data model
- Added `album` field to Song data model
- Enhanced MediaStore queries to fetch album metadata

### Architecture Updates
- Improved playlist state management with reactive updates
- Enhanced SharedPreferences handling for playlist persistence
- Better async album art loading with LaunchedEffect

### Code Quality
- Fixed all critical compilation errors
- Improved Compose best practices
- Maintained backward compatibility with existing saved playlists
- Enhanced animation and gesture handling

---

## 📥 Download

**APK File**: `IanPlayer-v1.0.1.apk` (attached below)

**Minimum Android Version**: Android 6.0 (API 23)

---

## 🚀 How to Install

1. Download the APK file from the assets below
2. Enable "Install from Unknown Sources" in your Android settings
3. Open the downloaded APK and follow installation prompts
4. Grant necessary permissions (Storage, Notification) when prompted

---

## 📝 Known Limitations

- Playlist image picker not yet implemented (prepared for future update)
- Manual scrollbar thumb dragging requires additional gesture detection
- Batch operations for multiple song selection could be enhanced in future versions

---

## 🙏 Notes

This is a maintenance release focused on stability and user experience improvements based on real-world usage feedback. All previously reported bugs have been addressed.

If you encounter any issues or have feature requests, please open an issue on GitHub!

---

## 💻 Tech Stack

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose (Material3)
- **Audio Engine**: Media3 ExoPlayer
- **Architecture**: MVVM with ViewModel + Repository pattern

---

**Full Changelog**: [v1.0.0...v1.0.1](https://github.com/ianocent/IanPlayer/compare/v1.0.0...v1.0.1)

Built with ❤️ for music lovers who want a clean, modern Android music player.

