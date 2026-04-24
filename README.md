# YQreader 小说阅读应用

一款专为阅读.txt文件和做笔记而设计的本地阅读应用。

包括两种风格——个人风格和开放风格。注意，笔记功能仅存在于后者中。

YQreader 是一个功能丰富的本地小说阅读应用，支持从本地文件夹导入小说并进行阅读。应用采用 ProductFlavors 架构，提供两个不同功能的版本。

## 🎯 版本说明

### Personal 版本 (个人版)
- **包名**: `com.cuteadog.novelreader.private`
- **特点**: 专注阅读体验，支持主题切换
- **核心功能**:
  - ✅ 主题切换功能
  - ✅ 字体调整功能
  - ✅ 章节导航
  - ❌ 笔记功能（禁用）
  - ❌ 高亮功能（禁用）
  - 文本选择：仅支持复制

### Open 版本 (开源版)
- **包名**: `com.cuteadog.novelreader`
- **特点**: 功能完整，支持笔记和高亮
- **核心功能**:
  - ✅ 主题切换功能
  - ✅ 字体调整功能
  - ✅ 章节导航
  - ✅ **笔记功能** - 支持创建、查看、删除笔记
  - ✅ **高亮功能** - 支持多色高亮和管理
  - 文本选择：支持高亮、笔记、复制

## ✨ 功能特性

### 基础功能
- 📚 **书架管理** - 小说列表按最近阅读时间排序
- 📂 **本地导入** - 从文件夹导入.txt格式小说
- 🖼️ **封面识别** - 自动识别文件夹中的icon.png作为封面
- 📖 **分页阅读** - 流畅的翻页动画体验
- 🔖 **阅读记忆** - 自动记录每本书的阅读位置
- 📋 **目录导航** - 侧边栏快速跳转章节
- 🗑️ **小说管理** - 支持删除不需要的小说

### 最新功能
- 🎨 **主题色统一** - 所有界面统一主题配色方案
- 🌈 **整页渐变** - 主页主题切换时的平滑过渡动画
- 💬 **气泡化列表** - 书架和笔记页采用圆角气泡设计
- ✍️ **高亮笔记一体化** - 高亮和笔记统一为单一数据模型
- 📱 **系统栏透明** - 全面支持Edge-to-Edge设计
- 📤 **分享功能** - 支持以文本或图片格式分享笔记
- 💾 **储存位置切换** - 可在内置存储和外部存储之间切换
- 📝 **更新日志** - 完整的版本更新记录页面
- 🎯 **文本选择增强** - 改进的文本选取和放大镜功能

## 📱 支持的Android版本

- **最低支持**: Android 8.0 (API 26)
- **目标版本**: Android 13 (API 33)
- **编译版本**: Android 14 (API 34)

## 🚀 快速开始

### 1. 导入小说
1. 点击主页"导入"按钮
2. 选择包含.txt文件的小说文件夹 (可以放入icon.png作为封面)
3. 应用会自动导入小说并显示在书架上

### 2. 开始阅读
1. 点击小说封面开始阅读
2. 在阅读界面，点击屏幕左右两侧进行翻页
3. 点击屏幕中央可打开/关闭目录

### 3. 使用笔记功能 (Open版本)
1. 长按文字选中文本
2. 选择"高亮"或"笔记"功能
3. 高亮支持多种颜色，笔记可添加随笔内容

### 4. 主题切换
1. 对于主题切换按钮，Personal风味显示在阅读页面，Open风味显示在主页
2. 在日间、护眼、夜间三种主题间循环切换

## 🏗️ 技术架构

### 技术栈
- **语言**: Kotlin
- **UI框架**: AndroidX + Material Design
- **数据存储**: DataStore + Kotlinx Serialization
- **异步处理**: Kotlin Coroutines
- **图片加载**: Coil
- **构建系统**: Gradle with ProductFlavors

### 项目结构
```
app/
├── src/main/          # 共享代码和资源
├── src/personal/      # Personal版本特有实现
└── src/open/          # Open版本特有实现

src/main/java/com/cuteadog/novelreader/
├── data/
│   ├── model/         # 数据模型 (Novel, Chapter, Highlight)
│   ├── dao/           # 数据访问对象
│   └── repository/    # 数据仓库
├── ui/
│   ├── library/       # 书架界面
│   ├── reader/        # 阅读界面
│   ├── notes/         # 笔记界面 (Open版本特有)
│   └── settings/      # 设置界面
├── theme/             # 主题管理
├── storage/           # 存储位置管理
└── util/              # 工具类
```

## 🔧 构建指南

### 构建Personal版本
```bash
./gradlew assemblePersonalDebug    # Debug版
./gradlew assemblePersonalRelease  # Release版
```

### 构建Open版本
```bash
./gradlew assembleOpenDebug        # Debug版
./gradlew assembleOpenRelease      # Release版
```

### 同时构建所有版本
```bash
./gradlew assemble
```

### APK输出位置
构建完成后，APK文件会自动复制到项目根目录的 `outputs/` 文件夹：
- `outputs/YQreader-personalDebug.apk`
- `outputs/YQreader-openDebug.apk`

## 🎨 主题系统

应用采用统一的主题管理系统，支持：

### 三种预设主题
- **日间主题**: 白色背景 + 蓝色主色调
- **护眼主题**: 浅黄色背景 + 柔和配色
- **夜间主题**: 深色背景 + 低对比度配色

### 主题特性
- 所有界面实时同步主题变化 (Personal风味仅限阅读页面)
- 弹窗、对话框统一主题配色
- 屏蔽系统深色模式二次反色
- 主页切换时整页渐变过渡

## 📝 笔记与高亮系统 (Open版本)

### 一体化设计
- 高亮和笔记统一为`Highlight`数据模型
- `noteContent`为空 → 纯高亮
- `noteContent`非空 → 笔记（同时显示为高亮）

### 功能特点
- 支持多种颜色高亮
- 长按已有高亮可快速选中
- 支持编辑已有笔记内容
- 可修改高亮颜色
- 删除重叠高亮

## 💾 储存管理

### 储存位置切换
- **内置存储**: 应用私有目录，卸载即清空
- **外部存储**: `/Android/data/<包名>/files/`，用户可见
- **支持迁移**: 切换位置时自动迁移所有文件

### 文件结构
```
<储存位置>/
├── novels/           # 小说目录树
└── shareBackgrounds/ # 分享背景图片
```

## 📊 更新记录

应用包含完整的更新日志页面，记录所有版本变更：

### v1.1 主要更新
- **新增**: 主题切换按钮与过渡动画、主页两个Tab、所有笔记页+管理/导出、笔记详情页、设置页扩充、局部放大镜等
- **修复**: 打开小说的闪烁问题、弹窗主题同步、阅读界面操作按钮阴影、长按不整页放大等

## ⚠️ 注意事项

1. **文件格式**: 请确保导入的文件夹中包含.txt格式的小说文件
2. **封面图片**: 可选在文件夹中添加icon.png作为封面图片
3. **存储权限**: 应用需要存储权限来访问文件系统
4. **版本共存**: Personal和Open版本使用不同包名，可在同一设备上共存
5. **数据兼容**: 两个版本的数据存储独立，不会相互影响

## 🛠️ 开发说明

### BuildConfig字段
```kotlin
// Personal版本
BuildConfig.ENABLE_THEME_SWITCH = true
BuildConfig.ENABLE_NOTES_HIGHLIGHT = false

// Open版本
BuildConfig.ENABLE_THEME_SWITCH = false
BuildConfig.ENABLE_NOTES_HIGHLIGHT = true
```

### 条件化代码示例
```kotlin
// 主题按钮 - 仅在personal版本中启用
if (BuildConfig.ENABLE_THEME_SWITCH) {
    // 主题切换逻辑
} else {
    btn_theme.visibility = View.GONE
}

// 笔记按钮 - 仅在open版本中启用
if (BuildConfig.ENABLE_NOTES_HIGHLIGHT) {
    // 笔记功能逻辑
} else {
    btn_notes.visibility = View.GONE
}
```

## 📞 支持与反馈

如遇到问题或需要进一步的帮助，请参考项目文档或检查：

1. Android SDK配置是否正确
2. Gradle构建配置是否完整
3. Java版本是否兼容
4. 存储权限是否已授予

---

**项目版本**: YQreader 1.2.1  
**最后更新**: 2026年4月  
**架构状态**: ✅ 版本分离实现完成  
**构建状态**: ✅ 支持双版本独立构建