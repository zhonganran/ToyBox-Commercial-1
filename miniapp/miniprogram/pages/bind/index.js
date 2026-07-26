const api = require('../../services/api')

Page({
  data: { bindingCode: '', submitting: false, error: '' },

  goBack() { wx.navigateBack() },

  onCodeInput(event) {
    const value = String(event.detail.value || '').replace(/[^0-9A-Za-z]/g, '').toUpperCase().slice(0, 8)
    this.setData({ bindingCode: value, error: '' })
  },

  submit() {
    const bindingCode = this.data.bindingCode
    if (bindingCode.length < 6) {
      this.setData({ error: '请输入车机端显示的完整绑定码' })
      return
    }
    this.setData({ submitting: true, error: '' })
    api.bindVehicle(bindingCode)
      .then(result => {
        wx.setStorageSync('wjx_reload_home', true)
        wx.setStorageSync('wjx_reload_profile', true)
        wx.showToast({ title: '绑定成功', icon: 'success' })
        setTimeout(() => wx.navigateBack(), 700)
      })
      .catch(error => this.setData({ error: error.message || '绑定失败，请重新生成绑定码' }))
      .finally(() => this.setData({ submitting: false }))
  }
})
