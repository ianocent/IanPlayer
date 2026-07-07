# GitHub Release Guide for v1.0.1

## Prerequisites Checklist

- [x] Version updated in `app/build.gradle.kts` (versionCode: 2, versionName: "1.0.1")
- [ ] Build release APK: `.\gradlew assembleRelease`
- [ ] APK will be located at: `app\build\outputs\apk\release\app-release.apk`
- [ ] Rename APK to: `IanPlayer-v1.0.1.apk`

## Steps to Create GitHub Release

### 1. Build the APK
```powershell
cd C:\Users\uzuma\AndroidStudioProjects\IanPlayer
.\gradlew assembleRelease
```

### 2. Rename the APK
```powershell
Copy-Item "app\build\outputs\apk\release\app-release.apk" "app\release\IanPlayer-v1.0.1.apk"
```

### 3. Create Release on GitHub

1. Go to: https://github.com/ianocent/IanPlayer/releases/new

2. **Choose a tag**: 
   - Create new tag: `v1.0.1`
   - Target: `main` (or your default branch)

3. **Release title**: 
   ```
   🎵 IanPlayer v1.0.1 - Bug Fixes & Feature Improvements
   ```

4. **Release description**: 
   - Copy the content from `RELEASE_NOTES_v1.0.1.md`
   - Paste it into the release description field

5. **Attach APK**:
   - Click "Attach binaries by dropping them here or selecting them"
   - Upload: `IanPlayer-v1.0.1.apk`

6. **Set as latest release**: ✓ Check this box

7. Click **"Publish release"**

## Alternative: Using GitHub CLI

If you have GitHub CLI installed:

```powershell
# Create the release
gh release create v1.0.1 `
  --title "🎵 IanPlayer v1.0.1 - Bug Fixes & Feature Improvements" `
  --notes-file RELEASE_NOTES_v1.0.1.md `
  app\release\IanPlayer-v1.0.1.apk

# Or if you want to create as draft first:
gh release create v1.0.1 `
  --draft `
  --title "🎵 IanPlayer v1.0.1 - Bug Fixes & Feature Improvements" `
  --notes-file RELEASE_NOTES_v1.0.1.md `
  app\release\IanPlayer-v1.0.1.apk
```

## Post-Release Tasks

1. Update README.md download link from v1.0.0 to v1.0.1:
   ```markdown
   [📦 Download APK (Latest Release)](https://github.com/ianocent/ianplayer/releases/download/v1.0.1/IanPlayer-v1.0.1.apk)
   ```

2. Tag the release in git:
   ```powershell
   git tag v1.0.1
   git push origin v1.0.1
   ```

3. Announce on your social media/community channels

## Verification

After publishing, verify:
- [ ] Release appears at: https://github.com/ianocent/IanPlayer/releases
- [ ] APK downloads correctly
- [ ] Release notes display properly with formatting
- [ ] "Latest" badge shows on v1.0.1

---

**Note**: The release notes have been prepared in `RELEASE_NOTES_v1.0.1.md` and are formatted to match your v1.0.0 release style.

