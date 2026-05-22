# 守护星 API 接口文档

## 基础信息

- **基础URL**: `http://localhost:3000/api`
- **WebSocket**: `ws://localhost:3001`
- **数据格式**: JSON

## HTTP API 接口

### 1. 服务状态检查

```
GET /api
```

**响应示例**:
```json
{
  "message": "守护星服务端运行中",
  "version": "1.0.0"
}
```

---

### 2. 设备管理

#### 2.1 绑定设备

```
POST /api/devices/bind
```

**请求参数**:
```json
{
  "deviceId": "设备唯一ID",
  "deviceName": "设备名称",
  "bindCode": "绑定码"
}
```

**响应示例**:
```json
{
  "success": true,
  "id": 1
}
```

#### 2.2 获取设备列表

```
GET /api/devices
```

**响应示例**:
```json
[
  {
    "id": 1,
    "device_id": "abc123",
    "device_name": "小明的手机",
    "bind_code": "123456",
    "is_online": 1,
    "is_locked": 0,
    "battery_level": 85,
    "created_at": "2024-01-01T12:00:00Z"
  }
]
```

#### 2.3 获取单个设备信息

```
GET /api/devices/:deviceId
```

**响应示例**:
```json
{
  "id": 1,
  "device_id": "abc123",
  "device_name": "小明的手机",
  "bind_code": "123456",
  "is_online": 1,
  "is_locked": 0,
  "battery_level": 85,
  "created_at": "2024-01-01T12:00:00Z"
}
```

---

### 3. 远程控制

#### 3.1 锁定设备

```
POST /api/devices/:deviceId/lock
```

**响应示例**:
```json
{
  "success": true
}
```

#### 3.2 解锁设备

```
POST /api/devices/:deviceId/unlock
```

**响应示例**:
```json
{
  "success": true
}
```

---

### 4. SOS求助

#### 4.1 获取SOS列表

```
GET /api/sos
```

**响应示例**:
```json
[
  {
    "id": 1,
    "device_id": "abc123",
    "device_name": "小明的手机",
    "latitude": 39.9042,
    "longitude": 116.4074,
    "address": null,
    "is_read": 0,
    "created_at": "2024-01-01T12:00:00Z"
  }
]
```

#### 4.2 发送SOS求助

```
POST /api/sos
```

**请求参数**:
```json
{
  "deviceId": "设备ID",
  "deviceName": "设备名称",
  "latitude": 39.9042,
  "longitude": 116.4074
}
```

**响应示例**:
```json
{
  "success": true,
  "id": 1
}
```

---

## WebSocket 协议

### 连接建立

客户端连接到 `ws://localhost:3001`

### 消息格式

所有消息使用JSON格式：

```json
{
  "type": "消息类型",
  "data": "消息数据"
}
```

### 客户端 -> 服务端

#### 注册设备

```json
{
  "type": "register",
  "deviceId": "设备ID"
}
```

### 服务端 -> 客户端

#### 锁定命令

```json
{
  "type": "COMMAND",
  "command": "LOCK"
}
```

#### 解锁命令

```json
{
  "type": "COMMAND",
  "command": "UNLOCK"
}
```

#### SOS提醒

```json
{
  "type": "SOS_ALERT",
  "deviceId": "设备ID",
  "deviceName": "设备名称",
  "latitude": 39.9042,
  "longitude": 116.4074
}
```

---

## 错误处理

### 错误响应格式

```json
{
  "error": "错误描述信息"
}
```

### HTTP状态码

| 状态码 | 说明 |
|--------|------|
| 200 | 成功 |
| 400 | 请求参数错误 |
| 404 | 资源不存在 |
| 500 | 服务器内部错误 |

---

## 数据模型

### Device (设备)

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Integer | 主键 |
| device_id | String | 设备唯一ID |
| device_name | String | 设备名称 |
| bind_code | String | 绑定码 |
| is_online | Integer | 是否在线(0/1) |
| is_locked | Integer | 是否锁定(0/1) |
| battery_level | Integer | 电量百分比 |
| created_at | DateTime | 创建时间 |

### SOSAlert (SOS求助)

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Integer | 主键 |
| device_id | String | 设备ID |
| device_name | String | 设备名称 |
| latitude | Real | 纬度 |
| longitude | Real | 经度 |
| address | String | 地址(可选) |
| is_read | Integer | 是否已读(0/1) |
| created_at | DateTime | 创建时间 |
