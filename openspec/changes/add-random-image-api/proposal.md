# Change: 添加支持横竖屏筛选的随机图片 API

## Why
局域网调用方需要直接获取图库中的随机图片，并能够按竖图或横图筛选，用于不同方向的展示区域。

## What Changes
- 复用现有 8000 端口服务，新增 `GET /api/images/random`，成功时直接返回原始图片字节及对应的 `image/*` Content-Type，不返回 JSON 地址。
- 可选查询参数 `orientation` 仅接受 `portrait` 或 `landscape`；省略时默认为 `landscape`，仅从横图（包含正方形）中随机选择，显式空值、重复参数及其他值返回 HTTP 400。
- 按 EXIF 旋转后的实际显示宽高判断：高大于宽属于 portrait，宽大于或等于高属于 landscape。
- 从应用具有读取权限的 MediaStore 图片集合中等概率随机选择，覆盖内置存储和已挂载存储卡，排除视频、未完成上传、不可读或尺寸无效的图片。新上传且已发布的图片可参与后续请求。
- 成功响应使用 `Cache-Control: no-store`，以流方式输出图片，避免将整张原图加载进内存；连续请求允许抽到同一张图。
- 没有符合条件的图片返回 HTTP 404，读取权限不足返回 HTTP 403，媒体库查询等服务端故障返回 HTTP 500；错误响应使用 JSON。其他请求方法返回 HTTP 405。
- 复用现有服务生命周期及访问校验，并补充接口调用示例。

## Impact
- Affected specs: 新增 `random-image-api`。
- Affected code: `WebSettingsServer.java`、`WebSettingsController.java`，新增 MediaStore 图片读取组件及相关测试。
- 与现有网页设置、图片上传接口并存；不引入第三方依赖。

## 调用示例
```text
GET http://<设备IP>:8000/api/images/random?orientation=portrait
GET http://<设备IP>:8000/api/images/random?orientation=landscape
GET http://<设备IP>:8000/api/images/random
```
