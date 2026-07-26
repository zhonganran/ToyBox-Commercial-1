const api = require('../../services/api')

function normalizeDataSet(payload) {
  const dataSet = payload || {}
  const summary = dataSet.summary || {}
  const average = summary.avgConsumption === null || summary.avgConsumption === undefined || summary.avgConsumption === ''
    ? null
    : Number(summary.avgConsumption)
  return Object.assign({}, dataSet, {
    summary: Object.assign({}, summary, {
      avgConsumption: average !== null && Number.isFinite(average) ? average.toFixed(1) : '--'
    }),
    items: (dataSet.items || []).map(item => {
      const consumption = item.consumption === null || item.consumption === undefined || item.consumption === ''
        ? null
        : Number(item.consumption)
      return Object.assign({}, item, {
        consumption: consumption !== null && Number.isFinite(consumption) ? consumption.toFixed(1) : '--'
      })
    }),
    charges: dataSet.charges || []
  })
}

Page({
  data: {
    loading: true,
    error: '',
    activeTab: 'trips',
    dataSet: null
  },

  onLoad() {
    const requestedTab = wx.getStorageSync('wjx_trip_tab')
    if (requestedTab === 'trips' || requestedTab === 'charges') {
      wx.removeStorageSync('wjx_trip_tab')
      this.setData({ activeTab: requestedTab })
    }
    this.loadData()
  },

  onShow() {
    if (this.getTabBar()) this.getTabBar().setData({ selected: 1 })
    const requestedTab = wx.getStorageSync('wjx_trip_tab')
    if (requestedTab === 'trips' || requestedTab === 'charges') {
      wx.removeStorageSync('wjx_trip_tab')
      if (requestedTab !== this.data.activeTab) {
        this.setData({ activeTab: requestedTab }, () => this.loadData())
      }
    }
  },

  onPullDownRefresh() {
    this.loadData().finally(() => wx.stopPullDownRefresh())
  },

  loadData() {
    this.setData({ loading: true, error: '' })
    return getApp().ensureSession()
      .then(() => api.getTrips({ type: this.data.activeTab }))
      .then(dataSet => this.setData({ dataSet: normalizeDataSet(dataSet), loading: false }))
      .catch(error => this.setData({ error: error.message || '行程数据加载失败', loading: false }))
  },

  selectTab(event) {
    const activeTab = event.currentTarget.dataset.tab
    if (!activeTab || activeTab === this.data.activeTab) return
    this.setData({ activeTab: activeTab }, () => this.loadData())
  },

  openTrip(event) {
    const id = event.currentTarget.dataset.id
    wx.navigateTo({ url: '/pages/trip-detail/index?id=' + encodeURIComponent(id) })
  },

  retry() {
    this.loadData()
  }
})
