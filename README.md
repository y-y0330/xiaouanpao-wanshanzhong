# 黄河科技学院南校区智能避障巡航系统 (FakeGPS v3.0)

## 项目结构
- `app/src/main/java/com/fakegps/engine/CampusRoadGraph.java`: 南校区拓扑路网模型、A*避障寻路、2-Opt TSP最优多点排序、GCJ-02转WGS-84反偏算法。
- `app/src/main/java/com/fakegps/service/TaskAccessibilityService.java`: 无障碍服务，动态抓取微信小程序界面的任务地标或坐标文本。
- `app/src/main/java/com/fakegps/service/FloatingWindowService.java`: 全局可拖拽悬浮窗，支持实时状态反馈、微信界面快捷选点、启停控制。
- `app/src/main/java/com/fakegps/service/LocationMockService.java`: 双通道模拟定位注入（GPS+Network），支持动态方向角、正弦配速波动。
- `.github/workflows/build.yml`: GitHub Actions 自动云端构建 APK 工作流。
- `hkd_south.kml`: 校准后的黄科大南校区标准闭环避障路径，可直接导入原版 APK 运行。

## 编译方法
1. **本地 Android Studio**: 直接打开此根目录，等待 Gradle 依赖同步后，点击 `Build -> Build APK(s)` 即可生成安装包。
2. **GitHub Actions 免环境云端打包**: 上传代码至 GitHub 仓库，在 Actions 标签页中会自动编译出 `app-debug.apk` 供直接下载。
