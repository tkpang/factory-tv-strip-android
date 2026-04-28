# 工厂测试 App 中文 UI 重构 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task by default. Only use superpowers:executing-plans when the user explicitly requests inline execution. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把工厂测试 App 改成步骤清晰、现代化、适合中文小白用户操作的界面。

**Architecture:** 保持现有 BLE/协议/dispatcher 不变，重构 Compose UI 和 ViewModel 文案。页面按“产品扫描、确认连接、测试灯光、解绑删除”四步组织。

**Tech Stack:** Kotlin, Jetpack Compose Material3.

---

## Task 1: 中文状态和流程文案

**Files:**
- Modify: `app/src/main/java/com/tkpang/tvstriptest/factory/FactoryViewModel.kt`
- Modify: `app/src/main/java/com/tkpang/tvstriptest/model/FactoryModels.kt`

- [ ] 将默认状态改为 `请先选择产品，然后扫描设备`。
- [ ] 扫描状态改中文：`正在扫描附近设备...`、`扫描已停止`、`没有权限或蓝牙未打开，无法扫描`。
- [ ] 操作状态改中文：`正在连接设备...`、`正在写入产品型号...`、`正在设置灯光...`、`正在测试最高亮度...`、`正在解绑设备...`。
- [ ] 总结状态改中文：`本次操作完成，共 X 台设备`、`有 X 台失败，请查看设备卡片错误原因`。

## Task 2: 重构 FactoryScreen 为四步卡片

**Files:**
- Modify: `app/src/main/java/com/tkpang/tvstriptest/ui/FactoryScreen.kt`

- [ ] 顶部标题改为 `工厂测试工具`。
- [ ] 增加总状态提示卡。
- [ ] 增加四个 StepCard：选择产品并扫描、确认设备并连接、测试灯光、解绑删除。
- [ ] 产品设置和扫描按钮放入第 1 步。
- [ ] 设备列表和连接按钮放入第 2 步。
- [ ] 设置 PID、普通设灯、最高亮度颜色放入第 3 步。
- [ ] 一键解绑放入第 4 步。

## Task 3: 设备卡片中文化和可视化

**Files:**
- Modify: `app/src/main/java/com/tkpang/tvstriptest/ui/FactoryScreen.kt`

- [ ] 每个设备显示图标：未连接 `📱`、连接中 `🔄`、已连接/成功 `✅`、失败 `⚠️`、已解绑 `🧹`。
- [ ] 设备状态用中文：待连接、连接中、已连接、测试成功、失败、已解绑。
- [ ] 显示 `信号 -45 dBm`，不裸露 `RSSI`。
- [ ] 显示 `上次结果` 和 `错误原因`。

## Task 4: 验证和发布

**Files:**
- Modify: `README.md`

- [ ] README 增加“新版中文步骤界面”说明。
- [ ] 运行 `git diff --check`。
- [ ] commit 并 push，等待 GitHub Actions 成功。
- [ ] 确认 `debug-latest` release 更新 APK。
