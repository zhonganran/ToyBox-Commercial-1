# 超级玩具箱平台服务

平台提供激活授权、应用更新、服务二维码和诊断日志管理。
所有公开 API、APK、二维码和支付回调统一使用 `https://wjx.689588.xyz`。部署时通过
`WJX_PLATFORM_PUBLIC_BASE_URL=https://wjx.689588.xyz` 配置唯一公开入口，不再区分 API 与 CDN 域名。
车辆端可通过 `POST /api/v1/logs/upload` 主动上传应用专属目录中的 `wjx.log`，后台按车辆和版本保存并支持查看、下载与删除。
平台接收日志时会隐藏 URL、部署域名、IPv4 和 IPv6；查看或下载时会再次实时脱敏，因此升级前已经上传的历史日志也不会显示服务器地址。

永久授权只绑定车辆 VIN。激活码或已验证支付负责首次绑定；之后客户端通过
`POST /api/v1/license/status` 提交 VIN 即可恢复授权。平台不签发设备令牌，不生成离线许可证，
也不需要客户端保存恢复凭证。支付状态接口仍使用每个订单独立的 `pollToken` 防止订单串用。

客户端在授权状态、激活和创建支付订单请求中同时上报车机真实系统属性，包括车辆名称、VIN
年款、车型变体、车系、动力类型和控制器版本。平台将这些字段按 VIN 保存，空读取不会覆盖已经
登记的非空数据；后台车辆列表支持按车型搜索，车辆详情页显示完整“车型配置”。这些字段属于
车辆端自报信息，平台不会根据 VIN 猜测销售款型。

平台首次启动时直接创建当前数据库结构。为保留已经售出的永久授权，只提供当前线上
schema 3、schema 4、schema 5 或 schema 6 到 schema 7 的一次性升级：先删除旧设备令牌列，再增加车型配置字段，
最后清理已经停用的在线音效与车辆档案表及无客户端消费者的旧功能开关，同时保留激活码、
VIN 绑定、订单和车辆记录；
不提供更旧结构或客户端本地数据的兼容迁移。
初始化使用数据库旁的跨进程文件锁串行执行；普通连接启用 SQLite WAL、外键检查和忙等待，
允许 Gunicorn 多 worker 安全并发启动。
数据库内保存当前结构版本标记；已有但未标记的数据库或版本不一致的数据库会被明确拒绝，
不会猜测结构或进行跨版本补丁。全新部署使用空数据库，由平台完成首次初始化。

## 易支付 V2 扫码激活

支付只使用易支付 V2 RSA 双向签名接口。商户私钥必须保存在服务器，不能写进 APK、源码或 Git。
服务进程需要配置以下环境变量：

```text
WJX_PAYMENT_PID=商户PID
WJX_PAYMENT_GATEWAY_BASE_URL=https://mzf.mapay.cc/xpay/epayn
WJX_PAYMENT_MERCHANT_PRIVATE_KEY_FILE=/var/lib/supertoybox/platform/payment_merchant_private.pem
WJX_PAYMENT_PLATFORM_PUBLIC_KEY_FILE=/var/lib/supertoybox/platform/payment_platform_public.pem
WJX_PAYMENT_ACTIVATION_PRICE=15.90
WJX_PAYMENT_ORDER_TTL_SECONDS=300
WJX_PAYMENT_CREATE_DEVICE_LIMIT=6
WJX_PAYMENT_CREATE_IP_LIMIT=60
WJX_PAYMENT_CREATE_WINDOW_SECONDS=3600
```

两个密钥文件应归服务账号所有，目录不可被其他用户写入，私钥权限设为 `600`。平台通过
`api/pay/create` 下单、`api/pay/query` 查单；异步通知只用于触发主动查单，最终以平台公钥验签后的
查询结果为准。确认 PID、商户订单号、金额和支付状态全部一致后，才会绑定 VIN 并生成永久授权。
V2 返回签名按字段名排序，仅包含业务字段；`code`、`sign` 和 `sign_type` 不参与签名。
客户端直接打开平台返回的收银页，订单未过期时重复点击会复用该订单；订单过期后才会重新下单。

统一域名后的支付回调地址为：

```text
https://wjx.689588.xyz/api/v1/license/payment/notify
https://wjx.689588.xyz/api/v1/license/payment/return
```

如果易支付商户后台配置了回调域名白名单，需要同步改为 `wjx.689588.xyz`。易支付网关
`WJX_PAYMENT_GATEWAY_BASE_URL` 仍然指向支付服务商地址，不应改成玩具箱域名。
公开入口和支付网关都必须使用无用户名、无密码、无查询参数和无片段的 HTTPS 地址；
公开入口只允许域名根路径，支付网关允许配置固定 API 基础路径。
新支付订单默认按 VIN 每小时 6 次、来源 IP 每小时 60 次限流；有效期内的待支付订单会直接复用，
不会重复调用支付供应商。
