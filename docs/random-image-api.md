# 随机图片 API

应用运行且网页服务开启时，通过设备的 8000 端口访问：

```sh
# 默认随机横图
curl --fail 'http://<设备IP>:8000/api/images/random' --output image.jpg
# 随机竖图
curl --fail 'http://<设备IP>:8000/api/images/random?orientation=portrait' --output portrait.jpg
# 显式指定横图
curl --fail 'http://<设备IP>:8000/api/images/random?orientation=landscape' --output landscape.jpg
# 裁剪到 1920×1080（默认选择横图）
curl --fail 'http://<设备IP>:8000/api/images/random?width=1920&height=1080' --output cropped.jpg
# 选择竖图并裁剪到 1080×1920
curl --fail 'http://<设备IP>:8000/api/images/random?orientation=portrait&width=1080&height=1920' --output portrait-cropped.jpg
```

`orientation` 可省略，默认 `landscape`，仅支持小写的 `portrait` 和 `landscape`。
显式空值、重复的方向参数或其他值返回 400。高大于宽属于竖图，宽大于或等于高属于横图（包含正方形），判断时考虑 EXIF 旋转信息。

成功返回 HTTP 200 和图片内容。响应使用 `Cache-Control: no-store`，通过关闭连接标识流结束。每次从可读取且符合方向的图片中等概率随机选择，允许连续返回同一张图片。

`width` 和 `height` 是输出像素宽高，必须同时提供，每项为 1–4096 的十进制正整数，总像素不能超过 8,388,608。空值、重复值、非整数、缺失一项或超限均返回 400。目标尺寸不改变选图方向，例如只传 `width=1080&height=1920` 仍从横图中选择后裁成竖图；需要竖图来源时应同时传 `orientation=portrait`。

传尺寸时，服务先校正 EXIF 旋转和镜像，再按目标比例居中裁剪并等比例缩放，输出精确宽高的 JPEG（质量 85，`Content-Type: image/jpeg`）。不拉伸、不留白，原图较小时允许放大；透明部分使用白底，动画图片仅取首帧。源文件不修改。裁剪使用有界采样解码，单次解码最多约 419 万像素，极端比例和大尺寸请求可能因采样而损失细节。原始文件处理上限为 256 MiB，超过或转换失败返回 500。

不传尺寸时直接返回原图字节和原始 `image/*` Content-Type，不转码；示例的 `.jpg` 文件名不代表原图一定为 JPEG，请以响应 Content-Type 为准。此模式的客户端应支持 EXIF 朝向。

图片来源为应用有权读取的 MediaStore 外部媒体库，包含内置共享存储与已挂载存储卡。视频、未完成上传、不可读取和尺寸无效的图片不参与选择；新上传图片完成媒体发布后可立即参与后续请求。

错误响应为 JSON，例如 `{"error":"没有符合方向的可读取图片"}`：

| 状态码 | 含义 |
| --- | --- |
| 400 | 方向、输出分辨率或请求格式无效 |
| 403 | 应用缺少图库读取权限，或现有服务访问校验拒绝请求 |
| 404 | 没有符合条件的可读取图片 |
| 405 | 请求方法不是 GET |
| 429 | 已有裁剪请求在处理或传输，请稍后重试 |
| 500 | 媒体库查询、图片转换失败或图片超过处理限制 |

接口复用现有局域网服务及其 Host、来源校验；使用设备 IP 和实际端口访问。服务关闭或应用退出后接口不可用。若图片在响应传输过程中被删除、存储卡断开或客户端断连，传输可能提前结束，需要重新请求。

为控制设备内存，同时只接收一个裁剪请求，原图、设置及上传接口仍可用。弱 Wi-Fi 下优先请求展示需要的分辨率以减少图片传输量。
