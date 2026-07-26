const home = {
  vehicle: {
    displayName: '2025款 宋Pro',
    vinMasked: 'LC0F****4345',
    onlineState: 'online',
    onlineLabel: '车辆在线',
    updatedLabel: '刚刚同步',
    soc: 78,
    rangeKm: 412,
    gearLabel: 'P 挡',
    lockLabel: '已锁车',
    runtimeLabel: '驻车中',
    insideTemp: 24,
    outsideTemp: 31,
    mileageKm: 18426,
    voltage12v: 14.1,
    connectionLabel: '稳定'
  },
  today: {
    distanceKm: 32.6,
    durationMinutes: 58,
    avgConsumption: 14.2,
    tripCount: 3
  }
}

const trips = {
  month: '2026年7月',
  summary: { distanceKm: 486, tripCount: 18, avgConsumption: 14.6 },
  latestRoute: {
    center: { latitude: 30.657, longitude: 104.066 },
    markers: [
      { id: 1, latitude: 30.641, longitude: 104.043, title: '起点', iconPath: '/assets/map/start.png', width: 28, height: 28 },
      { id: 2, latitude: 30.677, longitude: 104.092, title: '终点', iconPath: '/assets/map/end.png', width: 28, height: 28 }
    ],
    polyline: [{
      color: '#1769F5',
      width: 6,
      arrowLine: true,
      borderColor: '#FFFFFF',
      borderWidth: 2,
      points: [
        { latitude: 30.641, longitude: 104.043 },
        { latitude: 30.648, longitude: 104.052 },
        { latitude: 30.653, longitude: 104.064 },
        { latitude: 30.661, longitude: 104.071 },
        { latitude: 30.668, longitude: 104.083 },
        { latitude: 30.677, longitude: 104.092 }
      ]
    }]
  },
  items: [
    { id: 'trip-1', day: '26', month: '07月', startName: '高新南区', endName: '城南公园', distanceKm: 18.4, durationMinutes: 32, time: '15:42', consumption: 13.8, color: 'blue' },
    { id: 'trip-2', day: '25', month: '07月', startName: '家庭住址', endName: '商务中心', distanceKm: 26.7, durationMinutes: 44, time: '09:16', consumption: 14.6, color: 'green' },
    { id: 'trip-3', day: '24', month: '07月', startName: '商务中心', endName: '地下车库', distanceKm: 12.1, durationMinutes: 25, time: '18:03', consumption: 15.2, color: 'yellow' },
    { id: 'trip-4', day: '23', month: '07月', startName: '城西广场', endName: '家庭住址', distanceKm: 31.5, durationMinutes: 51, time: '20:28', consumption: 14.1, color: 'coral' }
  ],
  charges: [
    { id: 'charge-1', day: '25', month: '07月', type: '直流快充', energyKwh: 31.8, durationMinutes: 46, socText: '28% → 82%', cost: 38.4 },
    { id: 'charge-2', day: '19', month: '07月', type: '交流慢充', energyKwh: 42.6, durationMinutes: 312, socText: '19% → 100%', cost: 24.7 }
  ]
}

const tools = [
  { id: 'sounds', title: '音效库', subtitle: '管理系统音效与自定义音效', icon: 'volume-2', tone: 'coral', route: '/pages/web-content/index?type=sounds' },
  { id: 'automation', title: '自动化', subtitle: '查看规则与最近执行记录', icon: 'workflow', tone: 'green', route: '/pages/web-content/index?type=automation' },
  { id: 'diagnostics', title: '车机诊断', subtitle: '上传日志并查看处理状态', icon: 'file-up', tone: 'blue', route: '/pages/web-content/index?type=diagnostics' },
  { id: 'updates', title: '版本更新', subtitle: '查看新版功能与安装状态', icon: 'download', tone: 'yellow', route: '/pages/web-content/index?type=updates' },
  { id: 'guide', title: '使用说明', subtitle: '完整功能说明和操作步骤', icon: 'book-open', tone: 'cyan', route: '/pages/web-content/index?type=guide' },
  { id: 'service', title: '联系客服', subtitle: '授权、安装和问题咨询', icon: 'headphones', tone: 'violet', route: '/pages/web-content/index?type=service' }
]

const profile = {
  loggedIn: true,
  nickname: '微信用户',
  vehicle: { displayName: '2025款 宋Pro', vinMasked: 'LC0F****4345' },
  authorization: { status: 'activated', label: '已激活', expiresAt: '2027-07-26' },
  diagnostic: { lastUploadLabel: '最近上传成功', lastUploadAt: '今天 15:36' },
  version: { current: 'v1.9.8', latest: 'v1.9.8', updateAvailable: false }
}

module.exports = { home, trips, tools, profile }
