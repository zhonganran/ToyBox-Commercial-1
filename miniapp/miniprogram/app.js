const session = require('./services/session')

App({
  globalData: {
    sessionReady: null
  },

  onLaunch() {
    this.ensureSession().catch(() => {})
  },

  ensureSession() {
    if (!this.globalData.sessionReady) {
      this.globalData.sessionReady = session.ensureSession().catch(error => {
        this.globalData.sessionReady = null
        throw error
      })
    }
    return this.globalData.sessionReady
  }
})
