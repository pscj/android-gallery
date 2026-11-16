# Change: 随机图片 API 支持指定分辨率和居中裁剪

## Why
当前接口返回原图，在设备的弱 Wi-Fi 连接下，大图传输耗时较长。调用方需要直接获得适合展示区域尺寸的图片，减少传输量和客户端处理。

## What Changes
- 保留 `GET /api/images/random`，增加成对的可选参数 `width`、`height`，单位为像素，例如 `?width=1920&height=1080`。
- 不传尺寸时保持现有原图行为；传尺寸时返回精确宽高的 JPEG（质量 85），Content-Type 为 `image/jpeg`。
- 先按 EXIF 校正旋转及镜像，再按目标宽高比居中裁剪、等比例缩放；不拉伸、不留白，原图较小时允许放大。透明区域使用白底；动画图片仅输出静态首帧。
- 保留 `orientation` 的筛选规则，不传仍默认横图；目标分辨率不自动改变筛选方向。
- 两个尺寸必须同时提供，每项为 1–4096 的十进制正整数，总像素不超过 8,388,608；缺失一项、空值、小数、负数、重复或超限均返回 HTTP 400 JSON。
- 仅处理被选中的图片，采用有界采样和必要的区域解码，及时释放 Bitmap、输入流及临时输出；裁剪编码限制为单任务并发，忙碌时返回 HTTP 429，原图与设置接口继续可用。
- 转换失败返回 HTTP 500 JSON，不以原图或不完整 JPEG 冒充成功。图片传输开始后的连接错误只能结束传输。
- 更新调用文档，补充参数、裁剪、EXIF、错误及原图兼容验证。

## Impact
- Affected specs: `random-image-api`（依赖已实现的 `add-random-image-api`，该变更尚未归档；归档时按依赖顺序合并）。
- Affected code: `WebSettingsServer.java`、图片请求参数解析、`GalleryRandomImages.java`、新增裁剪组件、测试及 `docs/random-image-api.md`。
- 不修改设备上的源文件，不引入第三方依赖。

## 调用示例
```text
http://192.168.1.102:8000/api/images/random?width=1920&height=1080
http://192.168.1.102:8000/api/images/random?orientation=portrait&width=1080&height=1920
```
