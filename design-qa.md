# 首页设计 QA

- source visual truth path: `/Users/z/.codex/generated_images/019f63f8-f2a9-7511-aa09-0ae70f1f25d3/exec-38e97719-c14d-42e2-8abb-d307649fe494.png`
- selected wallpaper source path: `/Users/z/.codex/generated_images/019f63f8-f2a9-7511-aa09-0ae70f1f25d3/exec-355f47e7-d331-4c32-a444-fb395b8b5f3b.png`
- implementation screenshot path: `/Users/z/Desktop/优化版本/build/reports/design-qa/home-light-1920x1080.png`
- wallpaper implementation screenshot path: `/Users/z/Desktop/优化版本/build/reports/design-qa/home-wallpaper-option1-light.png`
- wallpaper dark theme screenshot path: `/Users/z/Desktop/优化版本/build/reports/design-qa/home-wallpaper-option1-dark.png`
- wallpaper comparison evidence: `/Users/z/Desktop/优化版本/build/reports/design-qa/home-wallpaper-option1-comparison.png`
- seven-wallpaper picker screenshot: `/Users/z/Desktop/优化版本/build/reports/design-qa/wallpaper-picker-seven.png`
- global wallpaper settings screenshot: `/Users/z/Desktop/优化版本/build/reports/design-qa/wallpaper-switch-canyon-settings.png`
- global wallpaper sound screenshot: `/Users/z/Desktop/优化版本/build/reports/design-qa/wallpaper-switch-canyon-sound.png`
- light-theme dark-wallpaper screenshot: `/Users/z/Desktop/优化版本/build/reports/design-qa/wallpaper-light-theme-tech-home.png`
- dark theme screenshot path: `/Users/z/Desktop/优化版本/build/reports/design-qa/home-dark-1920x1080.png`
- full-view comparison evidence: `/Users/z/Desktop/优化版本/build/reports/design-qa/home-full-comparison.png`
- focused card comparison evidence: `/Users/z/Desktop/优化版本/build/reports/design-qa/home-cards-comparison.png`
- focused hero comparison evidence: `/Users/z/Desktop/优化版本/build/reports/design-qa/home-hero-comparison.png`
- viewport: Android 模拟器 1920 × 1080；应用内容区受模拟器系统栏影响，比较时排除左侧系统导航区和底部系统任务栏。
- state: 首页浅色主题、车辆离线、无本地行程/充电数据、自定义车辆图片已配置。效果图中的演示数值没有写入应用。

## Findings

- 无遗留 P0/P1/P2 问题。首页信息顺序、主视觉、六项数据条、四张等宽卡片和本地洞察横栏均与确认稿的结构一致。
- 字体与排版：继续使用项目现有中文系统字体与全局 1.08 字号缩放；标题、主数值、单位和辅助文字层级清楚，无截断或重叠。
- 间距与布局：1920 × 1080 下四卡等宽，卡片与洞察完整落在底部导航上方；窄屏分支按相同顺序纵向滚动。
- 颜色与视觉令牌：保留原项目的青蓝粉动态渐变、玻璃表面和深色主题令牌。确认稿的山水背景仅作为构图参考，未替换原项目视觉体系。
- 图片质量：车辆图片继续使用用户可替换的真实位图；加载时自动裁掉透明空边，避免车辆过小或透明边造成的缩放偏差。QA 使用的车辆图片仅用于模拟器状态，没有打包进应用。
- 文案与内容：发动机、前电机、后电机转速、行程、充电、本地洞察均来自真实状态；空数据明确显示 `--` 或“暂无记录”。电芯压差统一为伏特格式，例如 `0.012 V`。
- 图标与交互：使用项目现有 Material 图标体系；电池健康、车身环境、行程轨迹和充电记录卡片均可点击进入对应页面。
- 可访问性：浅色和深色主题下文字对比度正常；主要卡片点击区域覆盖整张卡片；没有依赖颜色作为唯一状态表达。

## Interaction Evidence

- 底部导航：首页 → 设置 → 数据与更新 → 白天/黑暗主题 → 首页，切换正常。
- 充电记录卡片可进入“充电记录”页面，空状态正常。
- 行程与轨迹卡片可进入“行程地图”页面，空状态正常。
- 电池健康卡片可进入“车辆数据”页面，空状态正常。
- Logcat 未发现 `FATAL EXCEPTION` 或应用进程崩溃。

## Comparison History

1. 首次宽屏检查发现动力转速数值下沿被裁切、本地洞察明细高度不足。
   - 修复：明确动力标签和值的行高，并将宽屏洞察高度增加到 188dp。
   - 复查：数值、明细和两条建议均完整显示。
2. 第二次检查发现带透明空边的车辆图片显示偏小；直接按宽度铺满又会压住动力转速条。
   - 修复：图片加载/保存时自动裁剪透明空边，恢复等比适配，并将车辆整体上移 18dp。
   - 复查：车辆比例接近效果图，未遮挡胎压或动力转速信息。
3. 最终检查覆盖浅色、深色、空数据状态及四张卡片导航；没有剩余 P0/P1/P2 差异。
4. 用户复查后指出四个胎压标签距离车辆过远。
   - 修复：宽屏左右内边距由 20dp 收拢到 170dp，并按 1100dp、800dp、600dp 三个断点自适应回退。
   - 复查：四个标签围绕车辆左右分布，与车辆保持清楚间隔，同时不遮挡车辆、仪表或动力转速条。
5. 用户选择生成壁纸方案一，要求查看真实首页叠加效果。
   - 修复：将选定壁纸转换为 243KB 的 JPEG 资源并完成首页浅色、深色适配。
   - 复查：樱花和山景位于两侧，中心车辆、胎压、双仪表和动力转速保持清楚。
6. 用户要求将前后三轮共 7 张壁纸全部内置，并在所有页面自由切换。
   - 修复：7 张全尺寸壁纸压缩为 170–302KB，并为选择器单独生成 9–12KB 缩略图；选择写入设置数据库，由全局 scaffold 统一绘制，首页、个性音效、歌词显示、智能联动、设置和子页面共享同一壁纸。
   - 复查：设置页一次显示 7 个选择项；切换“金色峡谷”后设置页和个性音效页即时同步，重启后首页仍保留选择；白天主题搭配深色“科技极夜”时文字与卡片保持可读。

## Implementation Checklist

- [x] 动力总成读取随首页生命周期启停
- [x] 四张卡片固定顺序与等宽布局
- [x] 最近充电记录接入数据库 Flow
- [x] 本地洞察真实 metrics/recommendations 完整展示
- [x] 电芯压差统一使用 V
- [x] 空数据、加载中、浅色、深色状态验证
- [x] 7 张内置壁纸、全页面同步、重启持久化验证
- [x] 单元测试、Lint、Debug APK 构建通过

## Follow-up Polish

- P3：实车连接后再留一张全数据截图，可进一步微调极长转速、里程和洞察文案在真实数据下的视觉密度。

final result: passed

---

# 桌面悬浮卡片设计 QA

- reference image: `/tmp/codex-remote-attachments/019f7d06-53de-7423-a363-9544e8975175/D027C06B-27AA-4540-8DD8-165ABBD438F8/1-照片-1.jpg`
- implementation screenshot: `/Users/z/Desktop/签名密钥/SuperToyBox-WJX-SecondDev/app/build/reports/design-qa/desktop-quick-card-final.png`
- viewport: Android 模拟器 1280 × 720，160 dpi，桌面叠加层展开状态。

## Findings

- 去除包裹所有内容的大块外层背景，车辆控制和车外音效合并为一个连续四列网格。
- 每个功能使用独立深色半透明玻璃卡片、细浅色描边和 8dp 圆角，桌面背景可透过卡片显示。
- 图标、主标题、状态/分类副标题和右上状态点层级清楚；长控制名称自动换行，不再被省略。
- 展开图标位于卡片组顶部中央，点击同一图标即可收起，未恢复右上角关闭按钮。
- 空配置仍显示独立透明提示卡片；卡片数量不足四个时不补空白占位。
- 模拟器展开和收起均正常，未出现布局跳动、卡片互相覆盖或应用崩溃。

final result: passed
