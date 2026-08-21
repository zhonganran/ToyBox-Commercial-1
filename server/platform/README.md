# 超级玩具箱平台服务

平台提供激活授权、应用更新、在线音效库、服务二维码和诊断日志管理。
车辆端可通过 `POST /api/v1/logs/upload` 主动上传 `/sdcard/WJX/wjx.log`，后台按车辆和版本保存并支持查看、下载与删除。

平台首次启动时直接创建当前数据库结构，不包含旧表、旧字段补齐或历史数据迁移逻辑。
初始化使用数据库旁的跨进程文件锁串行执行；普通连接启用 SQLite WAL、外键检查和忙等待，
允许 Gunicorn 多 worker 安全并发启动。
数据库内保存当前结构版本标记；已有但未标记的数据库或版本不一致的数据库会被明确拒绝，
不会尝试兼容或迁移。部署本版本时请使用空数据库，由平台完成首次初始化。

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
```

两个密钥文件应归服务账号所有，目录不可被其他用户写入，私钥权限设为 `600`。平台通过
`api/pay/create` 下单、`api/pay/query` 查单；异步通知只用于触发主动查单，最终以平台公钥验签后的
查询结果为准。确认 PID、商户订单号、金额和支付状态全部一致后，才会绑定 VIN 并生成永久授权。
V2 返回签名按字段名排序，仅包含业务字段；`code`、`sign` 和 `sign_type` 不参与签名。
客户端直接打开平台返回的收银页，订单未过期时重复点击会复用该订单；订单过期后才会重新下单。
