const config = require('./config')
const request = require('./request')

function demoSession() {
  return {
    token: 'demo-session',
    demo: true,
    expiresAt: Date.now() + 24 * 60 * 60 * 1000
  }
}

function validSession(session) {
  return session && session.token && Number(session.expiresAt || 0) > Date.now() + 60000
}

function loginWithWechat() {
  return new Promise((resolve, reject) => {
    wx.login({
      success(loginResult) {
        if (!loginResult.code) {
          reject(new Error('微信登录未返回临时凭证'))
          return
        }
        request({
          path: '/api/v1/miniapp/login',
          method: 'POST',
          data: { code: loginResult.code }
        }).then(resolve).catch(reject)
      },
      fail(error) {
        reject(new Error(error.errMsg || '微信登录失败'))
      }
    })
  })
}

function ensureSession() {
  const cached = wx.getStorageSync(config.sessionStorageKey)
  if (validSession(cached)) return Promise.resolve(cached)
  if (config.demoMode) {
    const session = demoSession()
    wx.setStorageSync(config.sessionStorageKey, session)
    return Promise.resolve(session)
  }
  return loginWithWechat().then(result => {
    const session = {
      token: result.token,
      expiresAt: Number(result.expiresAt || 0),
      demo: false
    }
    wx.setStorageSync(config.sessionStorageKey, session)
    return session
  })
}

module.exports = { ensureSession }
