# Gallery

> **最新版本在 [`v2` 分支](https://github.com/pscj/android-gallery/tree/v2)。`main` 分支停留在 1.0，已不再维护。请使用 `v2` 获取最新代码、功能和修复。**
>
> 以下功能、构建和使用说明均以 `v2` 为准，不代表 `main` 1.0 的功能。

一个用于横屏展示本地照片与视频的 Android 图库应用，适合电子相册及大屏轮播场景。应用从系统媒体库随机选择素材，根据素材数量和横竖比例安排布局，并为部分留白区域生成模糊背景。

## 主要功能

- 全屏横屏展示，支持照片和视频混合轮播，每组最多一个视频。
- 提供随机、单图、双图、三图、四图五种显示模式。
- 根据照片 EXIF 方向及视频旋转信息判断素材方向，适配不同横竖组合。
- 支持照片模糊背景、部分照片组合的融合背景与交叉淡入，以及视频动态背景。
- 支持本机设置与局域网网页设置，共用显示模式和轮播间隔。
- 网页支持多选图片、选择文件夹上传及失败项重试。
- 提供局域网随机图片 API，支持横竖筛选和指定尺寸裁剪。

## 环境与构建

当前仓库配置如下：

| 项目 | 配置 |
| --- | --- |
| 应用名称 | Gallery |
| 包名 | `com.netflixbar.gallery` |
| 版本 | `2.0`（versionCode `2`） |
| 最低 Android API | 21（Android 5.0） |
| compileSdk / targetSdk | 34 / 28 |
| Android Gradle Plugin | 8.6.0 |
| Gradle Wrapper | 8.7 |
| 构建 JDK | 建议使用 JDK 17 |
| Java 源码兼容级别 | Java 8 |

克隆项目后先切换到 `v2` 分支：

```sh
git clone --branch v2 https://github.com/pscj/android-gallery.git
cd android-gallery
```

已有仓库可执行 `git switch v2`。使用 Android Studio 打开项目根目录，安装 Android SDK Platform 34，配置 Gradle JDK 后同步项目。首次构建需要下载 Gradle 和 Maven 依赖。

在项目根目录的 `local.properties` 中配置本机 SDK 路径，例如：

```properties
sdk.dir=/Users/your-name/Library/Android/sdk
```

请替换成自己的实际路径。`local.properties` 已被 Git 忽略，不应提交本机配置。

在项目根目录执行：

```sh
# 构建 Debug APK
./gradlew :app:assembleDebug

# 运行 JVM 单元测试
./gradlew :app:testDebugUnitTest

# 构建设备测试 APK
./gradlew :app:assembleDebugAndroidTest

# 在已连接的设备或模拟器上运行设备测试
./gradlew :app:connectedDebugAndroidTest
```

Debug APK 输出到 `app/build/outputs/apk/debug/app-debug.apk`。发布构建的签名需自行配置，仓库未提供发布签名配置。

## 使用方式

1. 安装并启动应用，按提示授予照片和视频访问权限。
2. 确保系统媒体库中存在可读取的照片或视频；已挂载存储卡中的媒体也可作为来源。
3. 长按图库画面打开设置，选择显示模式与更换间隔，然后保存。

默认使用四图模式，更换间隔为 5 分钟。可选间隔为 10 秒、30 秒、1 分钟、2 分钟、5 分钟、10 分钟、15 分钟、30 分钟和 1 小时。视频较长时，会根据视频时长延长本轮刷新间隔。

### 显示模式

“图”表示展示素材，可以是照片或视频。

| 模式 | 接口值 | 当前行为 |
| --- | --- | --- |
| 随机 | `random` | 每轮等概率选择 1～4，使用对应数量的展示规则 |
| 单图 | `single` | 一次展示一个素材；照片抽样仅接受校正方向后的横图，视频不受此照片过滤限制 |
| 双图 | `two` | 两项素材左右排列，按横竖组合分配区域 |
| 三图 | `three` | 三竖等高排列；三横或一竖两横采用左一右二布局；两竖一横整组重抽一次 |
| 四图 | `grid` | 目标抽取四项，按横竖组合使用自适应布局，部分组合实际展示三项 |

视频始终按原比例完整显示。照片根据布局采用完整显示或居中裁切，部分布局的留白使用自身或组合素材生成的模糊背景。

素材按轮随机抽取并在本轮内按路径去重，下一轮允许再次出现相同素材。素材不足、文件不可读或组合不支持时，不保证凑齐目标数量；不同展示分支有各自的等待或回退行为。

详细抽样、布局、裁切、缓存和切换规则见 [图库显示模式与播放规则](https://github.com/pscj/android-gallery/blob/v2/plan.md)。

## 局域网网页设置与上传

应用启动时自动开启网页服务，端口为 `8000`。在本机设置中可以关闭或重新开启服务，并查看当前设备网址。

将手机或电脑连接到同一局域网，在浏览器访问：

```text
http://<设备局域网 IP>:8000/
```

网页可修改显示模式和更换间隔，保存后与本机设置同步。服务随 Activity 销毁而停止；再次启动应用会重新开启。

### 图片上传

- 支持 JPEG、PNG、WebP、GIF 和 BMP，单个文件最大 50 MiB。
- 可多选图片，或通过支持文件夹选择的浏览器选择整个文件夹及其子文件夹。
- 上传目标为可写的可移除存储卡下的 `Pictures/GalleryUploads/`，没有可用存储卡时无法上传。
- 上传完成后发布到系统媒体库，后续轮播和随机图片请求可读取这些图片。
- 文件夹上传不会保留原目录层级，文件名会添加唯一后缀以避免重名。

网页服务没有登录认证，仅应在可信局域网使用。局域网内能访问该服务的客户端可以修改设置、上传图片并读取随机照片。

### HTTP 接口

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| `GET` | `/` | 网页设置和上传页面 |
| `GET` | `/api/settings` | 获取当前设置与间隔选项 |
| `POST` | `/api/settings` | 保存 `interval`（毫秒）和 `displayMode` |
| `GET` | `/api/uploads` | 获取上传目录、可用状态和文件大小限制 |
| `POST` | `/api/uploads` | 上传单张图片，正文为原始文件字节，`X-File-Name` 为 URL 编码后的文件名 |
| `GET` | `/api/images/random` | 返回符合方向条件的随机照片，可指定输出尺寸 |

随机图片接口示例：

```sh
# 随机横图，保留原始文件格式；请以响应 Content-Type 为准
curl --fail 'http://<设备IP>:8000/api/images/random' --output image.bin

# 随机竖图，居中裁剪并缩放为 1080×1920 的 JPEG
curl --fail 'http://<设备IP>:8000/api/images/random?orientation=portrait&width=1080&height=1920' --output portrait.jpg
```

参数限制、返回格式与错误码见 [随机图片 API](https://github.com/pscj/android-gallery/blob/v2/docs/random-image-api.md)。

## 项目结构

```text
app/
  src/main/
    java/com/netflixbar/gallery/
      FirstFragment.java              # 素材抽样、布局调度和播放
      GallerySettings.java            # 显示模式和轮播间隔存储
      SettingsDialog.java             # 本机设置界面
      WebSettingsController.java      # 网页服务生命周期与媒体发布通知
      WebSettingsServer.java          # HTTP 设置、上传和随机图片接口
      GalleryImageUploads.java        # 存储卡图片写入与媒体库发布
      GalleryRandomImages.java        # 随机照片选择
      VideoPreparation.java           # 视频尺寸、旋转、时长等元数据准备
      MediaAtmosphereController.java  # 媒体背景调度
      atmosphere/                     # 背景处理算法
    assets/settings.html              # 网页设置和上传界面
    res/                              # 布局、主题和图标
  src/test/                           # JVM 单元测试
  src/androidTest/                    # Android 设备测试
docs/random-image-api.md              # 随机图片接口文档
plan.md                               # 当前显示与播放规则
CHANGELOG.md                          # 变更行为和验证记录
AGENTS.md                             # 项目协作约定
```

## 开发与验证约定

- 后续变更统一追加到根目录 `CHANGELOG.md`，说明行为变化及验证结果；不再使用 OpenSpec 流程。
- 涉及显示或播放行为时，同步维护 `plan.md`，并按改动范围执行单元测试、构建或设备验证。
- 安装前核对构建产物绝对路径、包名 `com.netflixbar.gallery` 和目标设备。
- 安装后核对设备上的 APK 与本次构建产物一致，可拉取设备 APK 比较 SHA-256；不能仅凭版本号判断。
- 布局、视频兼容性与长时间运行稳定性需在目标设备上验证，构建成功不等于实际画面验收通过。
