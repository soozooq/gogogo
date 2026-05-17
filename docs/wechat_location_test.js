// pages/index/index.js
// 微信小程序定位测试代码

Page({
  data: {
    locationInfo: '点击按钮获取定位',
    coords: {}
  },

  getLocation() {
    const that = this;
    wx.getLocation({
      type: 'wgs84',  // 先获取 WGS84 原始坐标
      success(res) {
        const wgsInfo = `=== WGS84 原始坐标 ===\n纬度: ${res.latitude}\n经度: ${res.longitude}\n精度: ${res.accuracy}米\n速度: ${res.speed}m/s\n高度: ${res.altitude}`;

        console.log('微信 WGS84:', res.latitude, res.longitude);

        // 再获取 GCJ02 坐标
        wx.getLocation({
          type: 'gcj02',
          success(res2) {
            const gcjInfo = `\n=== GCJ02 坐标 ===\n纬度: ${res2.latitude}\n经度: ${res2.longitude}\n精度: ${res2.accuracy}米`;

            console.log('微信 GCJ02:', res2.latitude, res2.longitude);

            that.setData({
              locationInfo: wgsInfo + gcjInfo
            });
          }
        });
      },
      fail(err) {
        console.error('定位失败:', err);
        that.setData({
          locationInfo: '定位失败: ' + JSON.stringify(err)
        });
      }
    });
  },

  chooseLocation() {
    const that = this;
    wx.chooseLocation({
      success(res) {
        const addrInfo = `\n=== 地址解析 ===\n地址: ${res.address}\n名称: ${res.name}\n纬度: ${res.latitude}\n经度: ${res.longitude}`;

        console.log('微信地址:', res.address, res.name);
        console.log('微信地址坐标:', res.latitude, res.longitude);

        that.setData({
          locationInfo: that.data.locationInfo + addrInfo
        });
      }
    });
  }
});
