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
      version: '1.9.14',
      versionCode: 33,
      releaseNotes: '新增仪表卡片、实时喊话、VoxCPM AI 音效、自动化车内播放音效和比亚迪官方前照灯模式控制。',
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

function getRemoteAudio() {
  return source(
    () => request({ path: '/api/v1/miniapp/remote-audio' }),
    {
      success: true,
      vehicle: { displayName: '2025款 宋 Pro DM-i', onlineState: 'online', onlineLabel: '在线', updatedLabel: '刚刚同步' },
      authorization: { status: 'activated', label: '已激活', expiresAt: '永久' },
      canControl: true,
      sounds: [
        { id: 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa', soundKey: 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa', name: '欢迎上车', fileName: '欢迎上车.mp3', fileSize: 420000 },
        { id: 'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb', soundKey: 'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb', name: '请注意安全', fileName: '请注意安全.wav', fileSize: 360000 },
        { id: 'cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc', soundKey: 'cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc', name: '科技启动音', fileName: '科技启动音.m4a', fileSize: 760000 }
      ],
      command: null
    }
  )
}

function playRemoteSound(soundKey) {
  if (config.demoMode) {
    return delayed({ success: true, command: { id: Date.now(), status: 'pending', message: '正在等待车机', payload: { soundKey: soundKey } } })
  }
  return request({ path: '/api/v1/miniapp/remote-audio/play', method: 'POST', data: { soundKey: soundKey } })
}

function stopRemoteSound() {
  if (config.demoMode) {
    return delayed({ success: true, command: { id: Date.now(), status: 'completed', message: '车外播放已停止' } })
  }
  return request({ path: '/api/v1/miniapp/remote-audio/stop', method: 'POST' })
}

function getRemoteAudioCommand(commandId) {
  if (config.demoMode) {
    return delayed({ success: true, command: { id: commandId, status: 'completed', message: '已开始车外播放' } })
  }
  return request({ path: '/api/v1/miniapp/remote-audio/commands/' + encodeURIComponent(commandId) })
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
  getServiceQRCodes,
  getRemoteAudio,
  playRemoteSound,
  stopRemoteSound,
  getRemoteAudioCommand
}
