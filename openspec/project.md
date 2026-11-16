# Project Context

## Purpose
面向数字标牌场景的图库应用，可在全屏或多分屏布局中播放本地照片与视频，按照定时器轮播内容，并在需要时叠加逆地理编码生成的位置信息。

## Tech Stack
- 使用 Gradle（Android Gradle Plugin 8.6.0）构建的 Android 应用模块，目标 API 28，编译 SDK 34。
- 以 Java 8 编写核心代码，并依赖 AndroidX（AppCompat、Navigation、ConstraintLayout、GridLayout、ViewBinding）。
- 采用 ExoPlayer 2.x 处理视频播放与自适应轨道选择。
- 借助 OkHttp 3.x 搭配 Gson 处理网络请求与 JSON 解析。
- 引入 Material Components 与腾讯 Bugly CrashReport SDK。

## Project Conventions

### Code Style
Java 源码统一使用四个空格缩进、K&R 花括号风格以及语义明确的 camelCase 命名。
Android 资源文件采用 snake_case 命名（如 `layout_media_three_portrait.xml`），视图 `@+id` 以控件角色作为前缀（如 `videoView1`）。
视图访问通过 Jetpack ViewBinding（`ActivityMainBinding`、`FragmentFirstBinding`）完成，避免手动 `findViewById`。

### Architecture Patterns
采用单 Activity 架构（`MainActivity`），由 Jetpack Navigation 承载，`FirstFragment` 负责主要展示逻辑。
`FirstFragment` 从 `MediaStore` 检索媒体，借助自定义工具类解码图片，并为各分屏槽位管理 ExoPlayer 实例。
布局选择由数据驱动：根据素材横竖屏比例切换不同布局资源（`layout_media_three_portrait`、`layout_media_two_portrait`、`layout_media_portrait_left`、`layout_media_grid`）。
用户偏好（刷新间隔、展示模式）通过 `SettingsDialog` 写入 `SharedPreferences`，并实时同步至运行时状态。

### Testing Strategy
业务逻辑与工具方法在 `app/src/test` 下使用 JUnit 4（`testImplementation 'junit:junit:4.13.2'`）编写单元测试。
端到端与界面流程在 `app/src/androidTest` 中利用 AndroidX Test Espresso（`androidTestImplementation 'androidx.test.espresso:espresso-core:3.4.0'`）及 Navigation 相关测试验证。
针对权限申请、超大媒体库、长时间播放等自动化难以覆盖的场景，需要保留人工 QA。

### Git Workflow
默认分支为 `main`，建议创建以动词开头的短生命周期特性分支（如 `feature/update-media-refresh`），并在合并前提交 Pull Request 进行评审。
合并前将特性分支 rebase 到最新 `main`，最终采用 squash merge 保持线性历史。
提交信息使用祈使语气（如 `Add portrait layout cache`），并按功能聚合改动。

## Domain Context
面向自助终端或壁挂屏等需要持续轮播的展示场景，支持个人或策展式媒体目录。
内容通过 `MediaStore` 查询本地资源，结合 EXIF 朝向与元数据驱动布局挑选和地理标签显示。
当网络可用时，通过百度逆地理编码服务为图像附加可读地址信息。

## Important Constraints
在加载内容前必须获取运行时权限：Android 13 及以上需要 READ_MEDIA 权限，更早版本需 READ/WRITE_EXTERNAL_STORAGE。
`local.properties` 中需配置 `BAIDU_MAP_AK` 才能启用百度逆地理编码，否则位置信息将被禁用。
应用强制横屏（`android:screenOrientation="landscape"`），并针对大屏设备做了显示优化。
目标 SDK 维持在 28，尚未采用分区存储，更新依赖时需评估兼容性影响。

## External Dependencies
腾讯 Bugly CrashReport（`com.tencent.bugly:crashreport`），用于崩溃分析与上报。
Google ExoPlayer（`com.google.android.exoplayer:exoplayer`），负责音视频播放。
Square OkHttp（`com.squareup.okhttp3:okhttp`）与 Google Gson（`com.google.code.gson:gson`），提供 HTTP 通信与 JSON 解析能力。
百度地图逆地理编码 REST API，将 GPS 坐标转换为地址描述。
AndroidX Navigation、Material Components、ConstraintLayout 等库，支撑整体界面结构。
