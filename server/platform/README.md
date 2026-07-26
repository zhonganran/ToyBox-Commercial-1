# 超级玩具箱平台服务

## 微信小程序配置

服务启动前配置：

```text
WJX_MINIAPP_APP_ID=微信小程序AppID
WJX_MINIAPP_APP_SECRET=微信小程序AppSecret
WJX_MINIAPP_SESSION_DAYS=30
WJX_MINIAPP_BINDING_MINUTES=10
```

小程序使用微信临时登录凭证换取平台会话。平台数据库只保存微信 `openid` 和会话令牌哈希，不保存微信 `session_key`。车辆绑定码由已登记车机使用设备凭证生成，默认十分钟有效且只能使用一次。

## 新增接口

- `POST /api/v1/miniapp/login`
- `POST /api/v1/miniapp/vehicles/binding-code`
- `POST /api/v1/miniapp/vehicles/bind`
- `GET /api/v1/miniapp/dashboard`
- `GET /api/v1/miniapp/trips`
- `GET /api/v1/miniapp/trips/<id>`
- `GET /api/v1/miniapp/tools`
- `GET /api/v1/miniapp/profile`
- `POST /api/v1/miniapp/logs/request-upload`
- `POST /api/v1/vehicles/history-sync`

数据库迁移在平台启动时自动执行，保留原车辆、授权、更新、音效和诊断日志数据。
