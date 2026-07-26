const api = require('../../services/api')

const quickActions = [
  { id: 'trips', title: '行程记录', icon: 'route', tone: 'blue' },
  { id: 'charges', title: '充电记录', icon: 'zap', tone: 'green' },
  { id: 'sounds', title: '音效库', icon: 'volume-2', tone: 'coral' },
  { id: 'automation', title: '自动化', icon: 'workflow', tone: 'yellow' }
]

function optionalNumber(value) {
  if (value === null || value === undefined || value === '') return null
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : null
}

function normalizeDashboard(payload) {
  const vehicle = payload.vehicle || null
  const today = payload.today || {}
  if (!vehicle) return { vehicle: null, today: today }
  const soc = optionalNumber(vehicle.soc)
  const rangeKm = optionalNumber(vehicle.rangeKm)
  const mileageKm = optionalNumber(vehicle.mileageKm)
  const voltage12v = optionalNumber(vehicle.voltage12v)
  const insideTemp = optionalNumber(vehicle.insideTemp)
  const outsideTemp = optionalNumber(vehicle.outsideTemp)
  const todayDistance = optionalNumber(today.distanceKm)
  const todayConsumption = optionalNumber(today.avgConsumption)
  return {
    vehicle: Object.assign({}, vehicle, {
      soc: soc !== null ? Math.max(0, Math.min(100, soc)) : 0,
      socText: soc !== null ? Math.round(Math.max(0, Math.min(100, soc))) + '%' : '--',
      rangeText: rangeKm !== null ? Math.round(rangeKm).toString() : '--',
      mileageText: mileageKm !== null ? mileageKm.toLocaleString('zh-CN') + ' km' : '--',
      voltageText: voltage12v !== null ? voltage12v.toFixed(1) + 'V' : '--',
      insideTempText: insideTemp !== null ? Math.round(insideTemp) + '°' : '--',
      outsideTempText: outsideTemp !== null ? Math.round(outsideTemp) + '°' : '--'
    }),
    today: Object.assign({}, today, {
      distanceText: todayDistance !== null ? todayDistance.toFixed(1) : '--',
      consumptionText: todayConsumption !== null ? todayConsumption.toFixed(1) : '--'
    })
  }
}

Page({
  data: {
    loading: true,
    error: '',
    dashboard: null,
    quickActions: quickActions
  },

  onLoad() {
    this.loadDashboard()
  },

  onShow() {
    if (this.getTabBar()) this.getTabBar().setData({ selected: 0 })
    if (wx.getStorageSync('wjx_reload_home')) {
      wx.removeStorageSync('wjx_reload_home')
      this.loadDashboard()
    }
  },

  onPullDownRefresh() {
    this.loadDashboard().finally(() => wx.stopPullDownRefresh())
  },

  loadDashboard() {
    this.setData({ loading: true, error: '' })
    return getApp().ensureSession()
      .then(() => api.getHomeDashboard())
      .then(payload => this.setData({ dashboard: normalizeDashboard(payload), loading: false }))
      .catch(error => this.setData({ error: error.message || '车辆数据加载失败', loading: false }))
  },

  retry() {
    this.loadDashboard()
  },

  bindVehicle() {
    wx.navigateTo({ url: '/pages/bind/index' })
  },

  openTrips() {
    wx.switchTab({ url: '/pages/trips/index' })
  },

  openQuickAction(event) {
    const id = event.currentTarget.dataset.id
    if (id === 'trips') {
      wx.switchTab({ url: '/pages/trips/index' })
      return
    }
    if (id === 'charges') {
      wx.setStorageSync('wjx_trip_tab', 'charges')
      wx.switchTab({ url: '/pages/trips/index' })
      return
    }
    wx.navigateTo({ url: '/pages/web-content/index?type=' + id })
  }
})
