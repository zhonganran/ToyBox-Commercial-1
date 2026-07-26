Component({
  data: {
    selected: 0,
    list: [
      { pagePath: '/pages/home/index', text: '首页', icon: 'home' },
      { pagePath: '/pages/trips/index', text: '行程', icon: 'route' },
      { pagePath: '/pages/tools/index', text: '工具', icon: 'grid' },
      { pagePath: '/pages/profile/index', text: '我的', icon: 'user' }
    ]
  },

  methods: {
    switchTab(event) {
      const index = Number(event.currentTarget.dataset.index)
      const item = this.data.list[index]
      if (!item || index === this.data.selected) return
      wx.switchTab({ url: item.pagePath })
    }
  }
})
