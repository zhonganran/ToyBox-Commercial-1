function isTouristProject() {
  try {
    const account = wx.getAccountInfoSync()
    return !account || !account.miniProgram || account.miniProgram.appId === 'touristappid'
  } catch (error) {
    return true
  }
}

module.exports = {
  apiBaseUrl: 'https://api.689588.xyz',
  demoMode: isTouristProject(),
  requestTimeout: 12000,
  sessionStorageKey: 'wjx_miniapp_session',
  boundVehicleStorageKey: 'wjx_bound_vehicle'
}
