const config = require('./config')

function getToken() {
  const session = wx.getStorageSync(config.sessionStorageKey) || {}
  return session.token || ''
}

function request(options) {
  const token = getToken()
  return new Promise((resolve, reject) => {
    wx.request({
      url: config.apiBaseUrl + options.path,
      method: options.method || 'GET',
      data: options.data,
      timeout: config.requestTimeout,
      header: Object.assign(
        { 'Content-Type': 'application/json' },
        token ? { Authorization: 'Bearer ' + token } : {},
        options.header || {}
      ),
      success(response) {
        if (response.statusCode >= 200 && response.statusCode < 300) {
          resolve(response.data)
          return
        }
        if (response.statusCode === 401) {
          wx.removeStorageSync(config.sessionStorageKey)
          const app = getApp()
          if (app && app.globalData) app.globalData.sessionReady = null
        }
        const message = response.data && response.data.message
          ? response.data.message
          : '请求失败，请稍后重试'
        const error = new Error(message)
        error.statusCode = response.statusCode
        reject(error)
      },
      fail(error) {
        reject(new Error(error.errMsg || '网络连接失败'))
      }
    })
  })
}

module.exports = request
