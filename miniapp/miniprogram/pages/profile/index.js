const api = require('../../services/api')

Page({
  data: { loading: true, error: '', profile: null },
  onLoad() { this.loadProfile() },
  onShow() {
    if (this.getTabBar()) this.getTabBar().setData({ selected: 3 })
    if (wx.getStorageSync('wjx_reload_profile')) {
      wx.removeStorageSync('wjx_reload_profile')
      this.loadProfile()
    }
  },
  onPullDownRefresh() { this.loadProfile().finally(() => wx.stopPullDownRefresh()) },

  loadProfile() {
    this.setData({ loading: true, error: '' })
    return getApp().ensureSession()
      .then(() => api.getProfile())
      .then(profile => this.setData({ profile: profile, loading: false }))
      .catch(error => this.setData({ error: error.message || '账户信息加载失败', loading: false }))
  },

  retry() { this.loadProfile() },
  openBinding() { wx.navigateTo({ url: '/pages/bind/index' }) },
  openContent(event) { wx.navigateTo({ url: '/pages/web-content/index?type=' + event.currentTarget.dataset.type }) }
})
