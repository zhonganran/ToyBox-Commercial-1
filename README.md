# 超级玩具箱

面向 BYD DiLink 车机的车载工具应用。

## 使用说明

完整操作手册见 [使用说明](docs/user-guide.md)。安装应用后，也可以从“设置 → 使用说明”打开带搜索和分类筛选的应用内手册。

## 当前应用信息

- 应用显示名：超级玩具箱
- 包名：com.byd.wjx
- 项目名：SuperToyBox
- APK 命名：超级玩具箱-V版本号.apk
- 更新后台：https://api.689588.xyz/api/v1/update/latest

## 构建

```bash
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
```

正式包需要本地 `keystore.properties`，未配置签名时 release 构建会保持未签名。

车辆读取、运行时状态和 Helper 生命周期的模块边界见
[`docs/vehicle-runtime-architecture.md`](docs/vehicle-runtime-architecture.md)。

## 说明

更新检查已从原始仓库解绑，当前默认走香港服务器更新后台：

```properties
superToyBoxUpdateApiUrl=https://api.689588.xyz/api/v1/update/latest
```

应用内会读取后台返回的 `versionCode`、`version`、`downloadUrl` 和 `releaseNotes`，并下载后台指定的 APK。
客户端只使用统一商业平台更新源，不再保留旧版接口或旧兜底源。

当前项目以 Room Schema 17 作为全新安装基线，不读取或迁移旧 APK 的数据库与偏好设置。
从这个商业基线开始的后续版本，Room 结构变更必须提供显式迁移，不允许破坏性重建用户数据。

## 商业平台部署

首次启动 `server/platform` 前必须显式配置管理员密码，后台不再提供默认密码：

```bash
export WJX_PLATFORM_ADMIN_USER=admin
export WJX_PLATFORM_ADMIN_PASSWORD='replace-with-a-strong-password'
export WJX_PLATFORM_SECRET_KEY='replace-with-a-persistent-random-secret'
```

后台默认不信任客户端提交的代理 IP 请求头。仅当 Gunicorn 只能通过受信任的反向代理访问时，
按实际代理层数配置，例如单层 Nginx：

```bash
export WJX_PLATFORM_TRUSTED_PROXY_COUNT=1
```

不要在 Gunicorn 端口可被公网直接访问时启用该配置。

如果后台平台数据库仍使用历史默认密码，下次启动会要求上述环境变量，并自动替换为新密码。
