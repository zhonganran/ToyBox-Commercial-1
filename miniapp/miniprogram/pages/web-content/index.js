const api = require('../../services/api')

const contentMap = {
  sounds: { title: '音效库', caption: '车外音效管理', icon: 'volume-2', tone: 'coral', description: '查看系统音效与自定义音效同步状态。音效播放仍由车机端执行，小程序不会直接占用车内媒体会话。', rows: ['系统预设音效', '我的上传', '最近使用'] },
  automation: { title: '自动化', caption: '联动规则与记录', icon: 'workflow', tone: 'green', description: '查看已启用规则、触发条件和最近执行结果。所有规则在车机本地运行，断网不会影响已有联动。', rows: ['已启用规则', '最近执行', '待同步修改'] },
  diagnostics: { title: '车机诊断', caption: '日志上传与处理', icon: 'file-up', tone: 'blue', description: '请求车机上传 WJX/wjx.log。车辆离线时请求会进入等待状态，上线后再执行。', rows: ['最近上传', '问题备注', '处理状态'], action: '上传日志' },
  updates: { title: '版本更新', caption: '超级玩具箱安装包', icon: 'download', tone: 'yellow', description: '查看更新服务器发布的最新正式版本和更新内容。安装操作仍在车机端完成。', rows: ['当前版本', '最新版本', '更新说明'] },
  guide: { title: '使用说明', caption: '完整功能指南', icon: 'book-open', tone: 'cyan', description: '按功能分类查看安装授权、车辆数据、行程充电、车外音效、自动化、歌词和日志上传说明。', rows: ['安装与授权', '车辆数据', '行程与充电', '音效与自动化'] },
  service: { title: '联系客服', caption: '授权与问题咨询', icon: 'headphones', tone: 'violet', description: '从服务器读取最新客服二维码，避免二维码过期后仍显示旧图片。', rows: ['微信客服', '微信交流群', '问题反馈'] },
  authorization: { title: '授权信息', caption: '车辆使用权限', icon: 'key', tone: 'green', description: '查看当前车辆的激活、试用或到期状态。授权以服务器和车机离线许可证共同校验的结果为准。', rows: ['授权状态', '有效期', '绑定车辆'] }
}

Page({
  data: { type: '', content: null, actionLoading: false, actionStatus: '', update: null },

  onLoad(options) {
    const type = options.type || 'guide'
    const content = contentMap[type] || contentMap.guide
    this.setData({ type: type, content: content })
    if (type === 'updates') {
      api.getLatestUpdate()
        .then(update => this.setData({ update: update }))
        .catch(error => this.setData({ actionStatus: error.message || '版本信息加载失败' }))
    }
  },

  goBack() { wx.navigateBack() },

  runAction() {
    if (this.data.type !== 'diagnostics' || this.data.actionLoading) return
    this.setData({ actionLoading: true, actionStatus: '正在通知车机…' })
    api.uploadDiagnosticLog()
      .then(result => {
        this.setData({ actionStatus: result.message || '日志上传请求已发送' })
        wx.showToast({ title: '请求成功', icon: 'success' })
      })
      .catch(error => this.setData({ actionStatus: error.message || '请求失败' }))
      .finally(() => this.setData({ actionLoading: false }))
  }
})
