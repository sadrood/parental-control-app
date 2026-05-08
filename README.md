# 守护星 - 儿童监管应用

一款基于 Android 的儿童监管应用（家长控制中心），帮助家长实时监管儿童的设备使用情况。

## 功能特性

### 儿童端功能
- **应用使用监控**：实时追踪应用使用时长和频率
- **使用记录上传**：自动同步使用数据到云端
- **规则云端同步**：接收并执行家长设置的应用规则
- **全屏锁屏保护**：防止儿童绕过限制
- **联系家长**：一键拨打家长电话

### 家长端功能
- **远程锁屏/解锁**：随时控制儿童设备屏幕
- **应用规则管理**：设置应用使用限制和时间限额
- **使用统计**：查看儿童设备使用报告
- **实时定位**：获取儿童当前位置（需儿童设备授权）
- **安全事件提醒**：接收异常活动通知

### 安全功能
- 全屏锁屏覆盖（防返回、Home、多任务键拦截）
- 设备管理员权限保护
- 时间篡改防护
- 辅助功能服务监控

## 技术架构

### 网络通信
- **HTTP API**：Retrofit + OkHttp 与服务器通信
- **WebSocket**：实时双向通信，接收服务器推送
  - 远程锁屏/解锁指令
  - 规则更新通知
  - 使用数据同步
  - 安全事件推送

### 本地存储
- **Room 数据库**：本地规则、使用记录、安全事件持久化
- **SharedPreferences**：用户认证状态、设置参数

### 核心服务
- `MonitoringService`：应用使用监控前台服务
- `CloudSyncService`：云端数据同步服务
- `ScreenLockOverlayService`：全屏锁屏覆盖服务

## 项目结构

```
com.example.parentalcontrol/
├── data/
│   ├── dao/          # Room 数据库访问对象
│   ├── db/            # 数据库配置
│   └── entity/        # 数据实体类
├── network/
│   ├── model/        # API 请求/响应模型
│   ├── ApiClient.kt  # HTTP 客户端
│   ├── ApiService.kt # API 服务接口
│   ├── AuthManager.kt # 认证管理
│   └── WebSocketManager.kt # WebSocket 管理
├── receiver/         # 广播接收器
├── security/         # 安全检查模块
├── service/          # 前台服务
├── ui/
│   ├── auth/         # 配对登录页面
│   ├── parent/      # 家长端界面
│   └── child/       # 儿童端界面
└── util/            # 工具类
```

## 配对流程

1. **家长端**：
   - 选择"我是家长"登录
   - 获取 6 位配对码
   - 将配对码告知儿童

2. **儿童端**：
   - 选择"我是儿童"登录
   - 输入家长提供的配对码
   - 完成设备绑定


## 开发环境

- **Android Studio**：2024.x 或更高版本
- **Kotlin**：2.0.x
- **Gradle**：8.x
- **Android SDK**：API 34 (Android 14)
- **最低 SDK**：API 26 (Android 8.0)

## 依赖库

| 库名 | 版本 | 用途 |
|------|------|------|
| AndroidX Core KTX | 1.12.0 | Kotlin 扩展 |
| Navigation Component | 2.7.7 | 页面导航 |
| Room | 2.6.1 | 本地数据库 |
| Retrofit | 2.9.0 | HTTP 客户端 |
| OkHttp | 4.12.0 | 网络框架 |
| Socket.IO | 2.1.0 | WebSocket 客户端 |
| Material Components | 1.11.0 | Material Design UI |

## 权限说明

### 儿童端必需权限
- `DEVICE_ADMIN`：设备管理器权限（锁屏功能）
- `PACKAGE_USAGE_STATS`：使用情况访问权限（应用监控）
- `SYSTEM_ALERT_WINDOW`：悬浮窗权限（锁屏覆盖）
- `ACCESS_FINE_LOCATION`：精确定位
- `CALL_PHONE`：拨打电话

### 家长端权限
- 无特殊权限要求

## 构建说明

1. 克隆项目
```bash
git clone https://github.com/sadrood/parental-control-app.git
cd parental-control-app/app
```

2. 使用 Android Studio 打开项目

3. 等待 Gradle 同步完成

4. 连接 Android 设备或启动模拟器

5. 运行 `app` 模块

## 日志查看

在 Android Studio Logcat 中过滤以下 TAG：
- `ScreenLockOverlay`：锁屏服务日志
- `CloudSyncService`：云同步日志
- `WebSocketManager`：WebSocket 连接日志
- `ApiClient`：HTTP 请求日志

## 许可证

本项目仅供学习交流使用，请勿用于未经授权的设备监控。

## 联系方式

如有问题或建议，请提交 GitHub Issue。
