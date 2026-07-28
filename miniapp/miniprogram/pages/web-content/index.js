const api = require('../../services/api')

const contentMap = {
  sounds: { title: '远程播放音效', caption: '我的音效', icon: 'volume-2', tone: 'coral', description: '显示车机 WJX 文件夹中的本地音效，点击后由车机直接进行车外播放。', rows: [] },
  automation: { title: '自动化', caption: '联动规则与记录', icon: 'workflow', tone: 'green', description: '查看已启用规则、触发条件和最近执行结果。所有规则在车机本地运行，断网不会影响已有联动。', rows: ['已启用规则', '最近执行', '待同步修改'] },
  diagnostics: { title: '车机诊断', caption: '日志上传与处理', icon: 'file-up', tone: 'blue', description: '请求车机上传 WJX/wjx.log。车辆离线时请求会进入等待状态，上线后再执行。', rows: ['最近上传', '问题备注', '处理状态'], action: '上传日志' },
  updates: { title: '版本更新', caption: '超级玩具箱安装包', icon: 'download', tone: 'yellow', description: '查看更新服务器发布的最新正式版本和更新内容。安装操作仍在车机端完成。', rows: ['当前版本', '最新版本', '更新说明'] },
  guide: { title: '使用说明', caption: '完整功能指南', icon: 'book-open', tone: 'cyan', description: '按功能分类查看安装授权、车辆数据、行程充电、车外音效、自动化、歌词和日志上传说明。', rows: ['安装与授权', '车辆数据', '行程与充电', '音效与自动化'] },
  service: { title: '联系客服', caption: '授权与问题咨询', icon: 'headphones', tone: 'violet', description: '从服务器读取最新客服二维码，避免二维码过期后仍显示旧图片。', rows: ['微信客服', '微信交流群', '问题反馈'] },
  authorization: { title: '授权信息', caption: '车辆使用权限', icon: 'key', tone: 'green', description: '查看当前车辆的激活、试用或到期状态。授权以服务器和车机离线许可证共同校验的结果为准。', rows: ['授权状态', '有效期', '绑定车辆'] }
}

Page({
  data: {
    type: '',
    content: null,
    actionLoading: false,
    actionStatus: '',
    update: null,
    remoteAudioLoading: false,
    remoteAudio: null,
    activeSoundId: 0
  },

  onLoad(options) {
    const type = options.type || 'guide'
    const content = contentMap[type] || contentMap.guide
    this.setData({ type: type, content: content })
    if (type === 'updates') {
      api.getLatestUpdate()
        .then(update => this.setData({ update: update }))
        .catch(error => this.setData({ actionStatus: error.message || '版本信息加载失败' }))
    }
    if (type === 'sounds') this.loadRemoteAudio()
  },

  onUnload() {
    this.stopCommandPolling()
  },

  onPullDownRefresh() {
    if (this.data.type !== 'sounds') {
      wx.stopPullDownRefresh()
      return
    }
    this.loadRemoteAudio().finally(() => wx.stopPullDownRefresh())
  },

  goBack() { wx.navigateBack() },

  loadRemoteAudio() {
    this.setData({ remoteAudioLoading: true, actionStatus: '' })
    return getApp().ensureSession()
      .then(() => api.getRemoteAudio())
      .then(payload => {
        const normalized = Object.assign({}, payload, {
          sounds: (payload.sounds || []).map(sound => Object.assign({}, sound, {
            sizeLabel: sound.fileSize > 0 ? formatFileSize(sound.fileSize) : '大小未知'
          })),
          commandBusy: isCommandBusy(payload.command)
        })
        this.setData({ remoteAudio: normalized, remoteAudioLoading: false })
        this.scheduleCommandPolling(normalized.command)
      })
      .catch(error => this.setData({ actionStatus: error.message || '远程音效加载失败', remoteAudioLoading: false }))
  },

  playSound(event) {
    const soundKey = String(event.currentTarget.dataset.id || '')
    if (!soundKey || this.data.actionLoading) return
    this.setData({ actionLoading: true, activeSoundId: soundKey, actionStatus: '正在发送播放指令…' })
    api.playRemoteSound(soundKey)
      .then(result => {
        const remoteAudio = Object.assign({}, this.data.remoteAudio, { command: result.command, commandBusy: isCommandBusy(result.command) })
        this.setData({ remoteAudio: remoteAudio, actionStatus: result.command.message || '正在等待车机' })
        this.scheduleCommandPolling(result.command)
      })
      .catch(error => this.setData({ actionStatus: error.message || '播放指令发送失败' }))
      .finally(() => this.setData({ actionLoading: false, activeSoundId: 0 }))
  },

  stopRemoteSound() {
    if (this.data.actionLoading) return
    this.setData({ actionLoading: true, actionStatus: '正在通知车机停止播放…' })
    api.stopRemoteSound()
      .then(result => {
        const remoteAudio = Object.assign({}, this.data.remoteAudio, { command: result.command, commandBusy: isCommandBusy(result.command) })
        this.setData({ remoteAudio: remoteAudio, actionStatus: result.command.message || '停止指令已发送' })
        this.scheduleCommandPolling(result.command)
      })
      .catch(error => this.setData({ actionStatus: error.message || '停止指令发送失败' }))
      .finally(() => this.setData({ actionLoading: false }))
  },

  scheduleCommandPolling(command) {
    this.stopCommandPolling()
    if (!command || (command.status !== 'pending' && command.status !== 'delivered')) return
    this.commandPollTimer = setTimeout(() => this.pollCommand(command.id), 1800)
  },

  pollCommand(commandId) {
    api.getRemoteAudioCommand(commandId)
      .then(result => {
        const remoteAudio = Object.assign({}, this.data.remoteAudio, { command: result.command, commandBusy: isCommandBusy(result.command) })
        this.setData({ remoteAudio: remoteAudio, actionStatus: result.command.message || commandStatusLabel(result.command.status) })
        this.scheduleCommandPolling(result.command)
      })
      .catch(error => {
        this.setData({ actionStatus: error.message || '状态同步失败，正在重试' })
        this.commandPollTimer = setTimeout(() => this.pollCommand(commandId), 3500)
      })
  },

  stopCommandPolling() {
    if (this.commandPollTimer) clearTimeout(this.commandPollTimer)
    this.commandPollTimer = null
  },

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

function formatFileSize(bytes) {
  if (bytes < 1024 * 1024) return Math.max(1, Math.round(bytes / 1024)) + ' KB'
  return (bytes / 1024 / 1024).toFixed(1) + ' MB'
}

function commandStatusLabel(status) {
  return {
    pending: '正在等待车机',
    delivered: '车机已接收',
    completed: '已开始车外播放',
    failed: '播放失败',
    cancelled: '指令已取消',
    expired: '等待车机超时'
  }[status] || '等待状态更新'
}

function isCommandBusy(command) {
  return !!command && (command.status === 'pending' || command.status === 'delivered')
}
