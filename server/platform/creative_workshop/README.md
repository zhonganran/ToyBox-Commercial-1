# 创意工坊静态资源

该目录保存创意工坊的服务端部署文件，不会进入 Android APK。

- `catalog.json`：车型和彩绘目录，包含资源地址、版本、大小和 SHA-256。
- `viewer/`：透明 Three.js 车辆查看器。
- `supertoybox-platform.nginx.conf`：生产环境静态资源、CORS、缓存和 Range 请求配置。

生产资源部署在：

```text
/var/lib/supertoybox/platform/uploads/creative_workshop/
```

公开地址为 `https://cdn.689588.xyz/creative-workshop/`。Android 客户端只保存目录地址，
车型 GLB、彩绘和查看器由服务器按需下发并使用 WebView HTTP 缓存。
