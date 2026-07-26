const api = require('../../services/api')

Page({
  data: { loading: true, error: '', trip: null },
  onLoad(options) {
    const id = options.id || ''
    api.getTripDetail(id)
      .then(trip => {
        const rawConsumption = trip.consumption
        const consumption = rawConsumption === null || rawConsumption === undefined || rawConsumption === ''
          ? null
          : Number(rawConsumption)
        this.setData({
          trip: Object.assign({}, trip, {
            consumption: consumption !== null && Number.isFinite(consumption) ? consumption.toFixed(1) : '--'
          }),
          loading: false
        })
      })
      .catch(error => this.setData({ error: error.message || '行程详情加载失败', loading: false }))
  },
  goBack() { wx.navigateBack() }
})
