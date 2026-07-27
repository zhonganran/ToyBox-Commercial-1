const config = require('./config')
const request = require('./request')
const demo = require('./demo-data')

function delayed(value) {
  return new Promise(resolve => setTimeout(() => resolve(value), 220))
}

function source(realRequest, demoValue) {
  return config.demoMode ? delayed(demoValue) : realRequest()
}

function getHomeDashboard() {
  return source(() => request({ path: '/api/v1/miniapp/dashboard' }), demo.home)
}

function getTrips(params) {
  const query = params && params.type ? '?type=' + encodeURIComponent(params.type) : ''
  return source(() => request({ path: '/api/v1/miniapp/trips' + query }), demo.trips)
}

function getTools() {
  return source(
    () => request({ path: '/api/v1/miniapp/tools' }),
    {
      sync: {
        onlineState: 'online',
        statusLabel: '连接正常',
        description: '车辆在线，配置与记录已同步'
      },
      items: demo.tools
    }
  )
}

function getProfile() {
  return source(() => request({ path: '/api/v1/miniapp/profile' }), demo.profile)
}

function bindVehicle(bindingCode) {
  if (config.demoMode) {
    return delayed({ success: true, vehicle: demo.profile.vehicle })
  }
  return request({
    path: '/api/v1/miniapp/vehicles/bind',
    method: 'POST',
    data: { bindingCode: bindingCode }
  })
}

function getTripDetail(id) {
  if (config.demoMode) {
    const item = demo.trips.items.find(trip => trip.id === id) || demo.trips.items[0]
    return delayed(Object.assign({}, item, { route: demo.trips.latestRoute }))
  }
  return request({ path: '/api/v1/miniapp/trips/' + encodeURIComponent(id) })
}

function uploadDiagnosticLog() {
  if (config.demoMode) return delayed({ success: true, message: '日志上传成功' })
  return request({ path: '/api/v1/miniapp/logs/request-upload', method: 'POST' })
}

function getLatestUpdate() {
  return source(
    () => request({ path: '/api/v1/update/latest' }),
    {
      enabled: true,
      version: '1.9.13',
      versionCode: 32,
      releaseNotes: '智能联动新增车内播放音效，支持 WJX 音效库选择、循环播放和规则退出自动停止。',
      forceUpdate: false
    }
  )
}

function getServiceQRCodes() {
  return source(
    () => request({ path: '/api/v1/service/qrcodes' }),
    { items: [] }
  )
}

module.exports = {
  getHomeDashboard,
  getTrips,
  getTools,
  getProfile,
  bindVehicle,
  getTripDetail,
  uploadDiagnosticLog,
  getLatestUpdate,
  getServiceQRCodes
}
