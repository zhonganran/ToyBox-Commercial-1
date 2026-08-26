# 超级玩具箱

面向 BYD DiLink 车机的车载工具应用。

## 使用说明

完整操作手册见 [使用说明](docs/user-guide.md)。安装应用后，也可以从“设置 → 使用说明”打开带搜索和分类筛选的应用内手册。

## 当前应用信息

- 应用显示名：超级玩具箱
- 包名：com.byd.wjx
- 项目名：SuperToyBox
- APK 命名：超级玩具箱-V版本号.apk
- 统一商业服务：https://wjx.689588.xyz
- 更新后台：https://wjx.689588.xyz/api/v1/update/latest

## 构建

```bash
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
```

正式包需要本地 `keystore.properties`，未配置签名时 release 构建会保持未签名。

车辆读取、运行时状态和 Helper 生命周期的模块边界见
[`docs/vehicle-runtime-architecture.md`](docs/vehicle-runtime-architecture.md)。

## 说明

客户端的更新、激活、服务二维码、诊断日志上传全部从同一个商业服务域名派生：

```properties
superToyBoxServiceBaseUrl=https://wjx.689588.xyz
```

应用内会读取后台返回的 `versionCode`、`version`、`downloadUrl` 和 `releaseNotes`，并下载后台指定的 APK。
API、APK 和二维码地址必须保持在该 HTTPS 域名下，不接受跨域重定向。

当前项目以 Room Schema 21 创建全新数据库，只识别当前偏好键和当前格式的备份。
客户端不保留旧数据库、偏好设置、文件路径或备份格式迁移逻辑，发布前按全新安装验证。

## 商业平台部署

首次启动 `server/platform` 前必须显式配置管理员密码，后台不再提供默认密码：

```bash
export WJX_PLATFORM_ADMIN_USER=admin
export WJX_PLATFORM_ADMIN_PASSWORD='replace-with-a-strong-password'
export WJX_PLATFORM_SECRET_KEY='replace-with-a-persistent-random-secret'
export WJX_PLATFORM_PUBLIC_BASE_URL='https://wjx.689588.xyz'
```

后台默认不信任客户端提交的代理 IP 请求头。仅当 Gunicorn 只能通过受信任的反向代理访问时，
按实际代理层数配置，例如单层 Nginx：

```bash
export WJX_PLATFORM_TRUSTED_PROXY_COUNT=1
```

不要在 Gunicorn 端口可被公网直接访问时启用该配置。
