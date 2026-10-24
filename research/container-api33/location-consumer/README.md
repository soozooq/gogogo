# 独立的自有 Android 定位消费者

仅供云端研发，手机用户不需要安装。此 APK 与研究宿主分别构建，不依赖天外天、BlackBox、反射或自定义加载接口；普通 Activity 通过 `getSystemService(LOCATION_SERVICE)` 获取框架 `LocationManager`。

界面提供开始、刷新、停止、取消，展示 GPS／NETWORK 的缓存、单次回调和持续批量回调。坐标仅从 Android API 返回值读取；APK 不包含预期目标坐标，启动 Intent 只有关联本轮的随机 nonce。测试控制器在宿主设置合成目标，操作真实 Guest 界面并核对实际报告。

这个消费者是本项目自编测试应用。它验证真实 Guest 进程的调用链，不能代表第三方 SDK、微信、抖音或海外业务功能。Android 运行时权限申请流程也没有在本轮覆盖。宿主正常入口仍只准入固定计算器；消费者的导入和定位开启仅在云端 Android 测试代码内，完成后关闭定位并停止该 Guest。
