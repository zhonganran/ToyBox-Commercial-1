const api = require('../../services/api')

Page({
  data: {
    loading: true,
    error: '',
    sync: {
      onlineState: 'offline',
      statusLabel: '状态未知',
      description: '正在获取车机同步状态'
    },
    items: []
  },

  onLoad() { this.loadTools() },
  onShow() { if (this.getTabBar()) this.getTabBar().setData({ selected: 2 }) },
  onPullDownRefresh() { this.loadTools().finally(() => wx.stopPullDownRefresh()) },

  loadTools() {
    this.setData({ loading: true, error: '' })
    return getApp().ensureSession()
      .then(() => api.getTools())
      .then(result => this.setData({
        sync: result.sync || this.data.sync,
        items: result.items || [],
        loading: false
      }))
      .catch(error => this.setData({ error: error.message || '工具数据加载失败', loading: false }))
  },

  retry() {
    this.loadTools()
  },

  openTool(event) {
    const route = event.currentTarget.dataset.route
    if (route) wx.navigateTo({ url: route })
  }
})
