<p align="center">
  <img src="assets/PixelPlayerOSS.svg" alt="App Icon" height="200"/>
</p>

<h1 align="center">PixelPlayerOSS · 个人修改版</h1>

<p align="center">
  <img src="https://img.shields.io/badge/version-0.5.0--cvby-4FF0D0?style=for-the-badge" alt="Version">
  <img src="https://img.shields.io/badge/Android-11%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Android 11+">
  <img src="https://img.shields.io/badge/License-GPLv3-blue?style=for-the-badge" alt="GPLv3">
</p>

<p align="center">
  一个本地音乐播放器的个人美化分支，基于
  <a href="https://github.com/PixelPlayerHQ/PixelPlayerOSS">PixelPlayerHQ/PixelPlayerOSS</a>。
  只是自己用着顺手，慢慢加了点喜欢的东西，不是官方版本。
</p>

---

## 这是什么

上游 [PixelPlayerOSS](https://github.com/PixelPlayerHQ/PixelPlayerOSS) 是 [@lostf1sh](https://github.com/lostf1sh) 维护的开源 Android 音乐播放器：本地播放、自建音乐库、Material 3 Expressive 界面，默认离线。

这个分支（`personal-cvby`）在上面加了一些**外观自定义**的小功能，代码改动集中在主题层与播放页，播放核心没动。

- 包名：`com.lostf1sh.pixelplayeross`
- 版本：`0.5.0-cvby`
- 分支：`personal-cvby`（`main` 保持与上游同步）

## 相比上游多了什么

### 自定义背景

- 设置 → 外观 → 自定义背景：开关、从相册选图
- 图片会复制进应用私有目录，重启、撤销授权都不会丢
- 模糊程度 0–40、暗化程度 0–85%
- **页面背景不透明度**、**卡片/面板不透明度** 各 0–100%，自己拿捏壁纸透出多少

### 播放页外观

- **点击封面行为**：可切换成「打开歌词面板」（默认仍是专辑详情）
- **模糊封面背景**：把当前歌曲封面模糊后直接铺在播放页后面
- **播放页背景蒙版**：0–85% 黑色蒙版，文字看得清
- **播控按钮配色**：表现色 / 强调色 / 单色 三档

### 顺带的

- 播放页顶部音量数字，变化时带一点点动画
- 关于页有个人信息卡（维护者、来源、本版改动清单）
- 移除了页面级背景遮罩，遮罩统一交给壁纸层，避免叠加过暗

## 截图

占位中 —— 手头机器换主题比较频繁，等哪天固定下来再补 `assets/` 里的图 (｡･ω･｡)

## 构建

和上游一致，需要 JDK 21 + Android SDK：

```bash
./gradlew :app:assembleRelease
```

产物在 `app/build/outputs/apk/release/`。

本仓库用 GitHub Actions（`.github/workflows/nightly-apk.yml`）云构建，workflow_dispatch 可指定分支触发。

## 安装

**[下载最新 APK](https://github.com/cvbyisme/PixelPlayerOSS/releases/latest)**（选和手机架构对应的那个）

- 需要 Android 11+；模糊相关效果需 Android 12（API 31）及以上
- 提供 `arm64-v8a` 与 `armeabi-v7a` 两个版本，签名一致
- 这个分支的包是用**我自己的密钥**签的，和官方包、上一条 fork 的 CI 包签名都不一样；覆盖安装失败就先卸载原版再装（会丢本地设置）

## 已知限制

- 模糊依赖 `Modifier.blur` / RenderEffect，**只有 Android 12（API 31）及以上生效**，低版本会是未模糊的封面
- 模糊封面背景 + 高蒙版会给 GPU 一点压力，老机器建议把蒙版调低或关掉
- 只测过 arm64 设备

## 关于这个 fork

- 代码、文案、调试过程有 **AI 参与**，取舍和验收是我自己把关的
- 提 issue / PR 欢迎，但优先看上游：上游修复的问题我不一定跟得及时
- 本项目不提供付费支持，也不代表上游立场

## 许可证

GPL-3.0，与上游一致。上游完整说明见 [README.upstream.md](README.upstream.md)。

## 致谢

- [@lostf1sh](https://github.com/lostf1sh) 与 [PixelPlayerHQ](https://github.com/PixelPlayerHQ)：上游项目本体
- 上游 README 里列出的所有依赖与贡献者

---

<p align="center">
  <sub>个人维护：<a href="https://github.com/cvbyisme">cvby</a> · <a href="https://cvby.pages.dev">cvby.pages.dev</a></sub>
</p>
