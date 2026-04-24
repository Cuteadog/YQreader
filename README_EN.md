# YQreader Novel Reading Application

A local reader app used simply for reading .txt files and taking notes.

Includes 2 flavors--personal and open. Note function is only exists in the latter one.

The application uses ProductFlavors architecture to provide two different functional versions.

## 🎯 Version Description

### Personal Version (Private Edition)
- **Package Name**: `com.cuteadog.novelreader.private`
- **Characteristics**: Focus on reading experience, supports theme switching
- **Core Features**:
  - ✅ Theme switching function
  - ✅ Font adjustment function
  - ✅ Chapter navigation
  - ❌ Note function (disabled)
  - ❌ Highlight function (disabled)
  - Text selection: Only supports copy

### Open Version (Open Source Edition)
- **Package Name**: `com.cuteadog.novelreader`
- **Characteristics**: Full functionality, supports notes and highlights
- **Core Features**:
  - ✅ Theme switching function
  - ✅ Font adjustment function
  - ✅ Chapter navigation
  - ✅ **Note function** - Supports creating, viewing, and deleting notes
  - ✅ **Highlight function** - Supports multi-color highlights and management
  - Text selection: Supports highlight, note, copy

## ✨ Features

### Basic Features
- 📚 **Bookshelf management** - Novel list sorted by recent reading time
- 📂 **Local import** - Import .txt format novels from folders
- 🖼️ **Cover recognition** - Automatically recognizes icon.png in folder as cover
- 📖 **Pagination reading** - Smooth page flipping animation experience
- 🔖 **Reading memory** - Automatically records reading position for each book
- 📋 **Directory navigation** - Quick chapter jump via sidebar
- 🗑️ **Novel management** - Supports deleting unwanted novels

### Newest Features
- 🎨 **Theme color unification** - Unified theme color scheme across all interfaces
- 🌈 **Full-page gradient** - Smooth transition animation when switching themes on home page
- 💬 **Bubble-style lists** - Bookshelf and notes pages use rounded bubble design
- ✍️ **Highlight-note integration** - Highlights and notes unified into single data model
- 📱 **Transparent system bars** - Full Edge-to-Edge design support
- 📤 **Sharing function** - Supports sharing notes in text or image format
- 💾 **Storage location switching** - Can switch between internal and external storage
- 📝 **Update log** - Complete version update record page
- 🎯 **Enhanced text selection** - Improved text selection and magnifier function

## 📱 Supported Android Versions

- **Minimum Support**: Android 8.0 (API 26)
- **Target Version**: Android 13 (API 33)
- **Compile Version**: Android 14 (API 34)

## 🚀 Quick Start

### 1. Import Novels
1. Click the "Import" button on the home page
2. Select a novel folder containing .txt files (You can use icon.png as the cover)
3. The app will automatically import novels and display them on the bookshelf

### 2. Start Reading
1. Click the novel cover to start reading
2. In the reading interface, click left and right sides of the screen to flip pages
3. Click the center of the screen to open/close directory

### 3. Using Note Features (Open Version)
1. Long press text to select
2. Choose "Highlight" or "Note" function
3. Highlights support multiple colors, notes can add thoughts

### 4. Theme Switching
1. For the theme toggle button, the Personal flavor is displayed on the reading page, while the Open flavor is displayed on the homepage
2. Cycle between daytime, eye-care, and night themes

## 🏗️ Technical Architecture

### Technology Stack
- **Language**: Kotlin
- **UI Framework**: AndroidX + Material Design
- **Data Storage**: DataStore + Kotlinx Serialization
- **Async Processing**: Kotlin Coroutines
- **Image Loading**: Coil
- **Build System**: Gradle with ProductFlavors

### Project Structure
```
app/
├── src/main/          # Shared code and resources
├── src/personal/      # Personal version specific implementation
└── src/open/          # Open version specific implementation

src/main/java/com/cuteadog/novelreader/
├── data/
│   ├── model/         # Data models (Novel, Chapter, Highlight)
│   ├── dao/           # Data access objects
│   └── repository/    # Data repositories
├── ui/
│   ├── library/       # Bookshelf interface
│   ├── reader/        # Reading interface
│   ├── notes/         # Notes interface (Open version specific)
│   └── settings/      # Settings interface
├── theme/             # Theme management
├── storage/           # Storage location management
└── util/              # Utility classes
```

## 🔧 Build Guide

### Build Personal Version
```bash
./gradlew assemblePersonalDebug    # Debug version
./gradlew assemblePersonalRelease  # Release version
```

### Build Open Version
```bash
./gradlew assembleOpenDebug        # Debug version
./gradlew assembleOpenRelease      # Release version
```

### Build All Versions
```bash
./gradlew assemble
```

### APK Output Location
After building, APK files are automatically copied to the `outputs/` folder in the project root:
- `outputs/YQreader-personalDebug.apk`
- `outputs/YQreader-openDebug.apk`

## 🎨 Theme System

The application uses a unified theme management system, supporting:

### Three Preset Themes
- **Daytime Theme**: White background + blue main color
- **Eye-care Theme**: Light yellow background + soft color scheme
- **Night Theme**: Dark background + low contrast color scheme

### Theme Characteristics
- All interfaces synchronize theme changes in real-time (Personal flavor is only available on the reading page)
- Dialogs and popups unified with theme colors
- Blocks system dark mode secondary inversion
- Full-page gradient transition when switching themes on home page

## 📝 Notes and Highlights System (Open Version)

### Integrated Design
- Highlights and notes unified into the `Highlight` data model
- `noteContent` empty → Pure highlight
- `noteContent` not empty → Note (also displayed as highlight)

### Feature Highlights
- Supports multiple color highlights
- Long press existing highlights to quickly select
- Supports editing existing note content
- Can modify highlight colors
- Delete overlapping highlights

## 💾 Storage Management

### Storage Location Switching
- **Internal Storage**: App private directory, cleared when uninstalled
- **External Storage**: `/Android/data/<package>/files/`, visible to users
- **Migration Support**: Automatically migrates all files when switching locations

### File Structure
```
<storage location>/
├── novels/           # Novel directory tree
└── shareBackgrounds/ # Sharing background images
```

## 📊 Update Record

The application includes a complete update log page, recording all version changes:

### v1.1 Major Updates
- **Added**: Theme switch button with transition animation, two tabs on home page, all notes page + management/export, note detail page, settings page expansion, local magnifier, etc.
- **Fixed**: Novel opening flash issue, popup theme synchronization, reading interface operation button shadows, long press not full page zoom, etc.

## ⚠️ Notes and Precautions

1. **File Format**: Ensure imported folders contain .txt format novel files
2. **Cover Image**: Optionally add icon.png in folder as cover image
3. **Storage Permission**: App requires storage permission to access file system
4. **Version Coexistence**: Personal and Open versions use different package names and can coexist on the same device
5. **Data Compatibility**: Two versions have independent data storage and do not affect each other

## 🛠️ Development Notes

### BuildConfig Fields
```kotlin
// Personal version
BuildConfig.ENABLE_THEME_SWITCH = true
BuildConfig.ENABLE_NOTES_HIGHLIGHT = false

// Open version
BuildConfig.ENABLE_THEME_SWITCH = false
BuildConfig.ENABLE_NOTES_HIGHLIGHT = true
```

### Conditional Code Example
```kotlin
// Theme button - only enabled in personal version
if (BuildConfig.ENABLE_THEME_SWITCH) {
    // Theme switching logic
} else {
    btn_theme.visibility = View.GONE
}

// Note button - only enabled in open version
if (BuildConfig.ENABLE_NOTES_HIGHLIGHT) {
    // Note function logic
} else {
    btn_notes.visibility = View.GONE
}
```

## 📞 Support and Feedback

If you encounter issues or need further assistance, please refer to the project documentation or check:

1. Whether Android SDK is configured correctly
2. Whether Gradle build configuration is complete
3. Whether Java version is compatible
4. Whether storage permissions have been granted

---

**Project Version**: YQreader 1.2.1  
**Last Updated**: April 2026  
**Architecture Status**: ✅ Version separation implementation complete  
**Build Status**: ✅ Supports dual version independent building