const express = require('express');
const http = require('http');
const WebSocket = require('ws');
const cors = require('cors');
const bodyParser = require('body-parser');
const sqlite3 = require('sqlite3').verbose();

const app = express();
const server = http.createServer(app);

// 显式处理 WebSocket 升级 —— 比 { server } 方式更可靠
const wss = new WebSocket.Server({ noServer: true });

server.on('upgrade', (request, socket, head) => {
    console.log(`[WS] 升级请求: ${request.url} from ${request.socket.remoteAddress}`);
    console.log(`[WS] Headers:`, JSON.stringify(request.headers));

    wss.handleUpgrade(request, socket, head, (ws) => {
        wss.emit('connection', ws, request);
        console.log('[WS] 升级成功');
    });
});

wss.on('error', (err) => {
    console.error('[WS] 服务器错误:', err.message);
});

app.use(cors());
app.use(bodyParser.json());

const db = new sqlite3.Database('./guardian_star.db');

db.serialize(() => {
    db.run(`CREATE TABLE IF NOT EXISTS devices (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        device_id TEXT UNIQUE,
        device_name TEXT,
        bind_code TEXT,
        parent_id TEXT,
        is_online INTEGER DEFAULT 0,
        is_locked INTEGER DEFAULT 0,
        battery_level INTEGER DEFAULT 0,
        created_at DATETIME DEFAULT CURRENT_TIMESTAMP
    )`);

    db.run(`CREATE TABLE IF NOT EXISTS app_limits (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        device_id TEXT,
        package_name TEXT,
        app_name TEXT,
        daily_limit_minutes INTEGER DEFAULT 60,
        used_minutes_today INTEGER DEFAULT 0,
        is_limit_enabled INTEGER DEFAULT 1,
        is_whitelist INTEGER DEFAULT 0,
        updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
        UNIQUE(device_id, package_name)
    )`);

    db.run(`CREATE TABLE IF NOT EXISTS schedules (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        device_id TEXT,
        name TEXT,
        start_hour INTEGER,
        start_minute INTEGER,
        end_hour INTEGER,
        end_minute INTEGER,
        type INTEGER DEFAULT 0,
        enabled INTEGER DEFAULT 1,
        repeat_monday INTEGER DEFAULT 0,
        repeat_tuesday INTEGER DEFAULT 0,
        repeat_wednesday INTEGER DEFAULT 0,
        repeat_thursday INTEGER DEFAULT 0,
        repeat_friday INTEGER DEFAULT 0,
        repeat_saturday INTEGER DEFAULT 0,
        repeat_sunday INTEGER DEFAULT 0,
        created_at DATETIME DEFAULT CURRENT_TIMESTAMP
    )`);

    db.run(`CREATE TABLE IF NOT EXISTS sos_alerts (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        device_id TEXT,
        device_name TEXT,
        latitude REAL,
        longitude REAL,
        address TEXT,
        is_read INTEGER DEFAULT 0,
        created_at DATETIME DEFAULT CURRENT_TIMESTAMP
    )`);

    db.run(`CREATE TABLE IF NOT EXISTS usage_stats (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        device_id TEXT,
        package_name TEXT,
        app_name TEXT,
        usage_seconds INTEGER,
        date TEXT,
        created_at DATETIME DEFAULT CURRENT_TIMESTAMP
    )`);
});

const deviceSockets = new Map();
const parentSockets = new Set();
const deviceInfoMap = new Map();  // device_id → { device_name, battery, ... }

wss.on('connection', (ws, req) => {
    const clientIp = req.socket.remoteAddress;
    console.log(`[WS] 新客户端已连接 (IP: ${clientIp})`);

    ws.on('message', (message) => {
        try {
            const data = JSON.parse(message);
            console.log(`[WS] 收到消息 type=${data.type} device_id=${data.device_id || data.deviceId || '-'}`);

            switch (data.type) {
                case 'register_parent':
                    parentSockets.add(ws);
                    console.log('[WS] 家长端已注册');
                    break;

                case 'register_child':
                    deviceSockets.set(data.device_id, ws);
                    deviceInfoMap.set(data.device_id, { device_name: data.device_name || '未知设备', battery: 100 });
                    console.log(`[WS] 设备已注册: ${data.device_id}`);
                    updateDeviceStatus(data.device_id, true);
                    break;

                case 'STATUS_REPORT':
                    handleStatusReport(data);
                    break;

                case 'COMMAND':
                    handleCommand(data);
                    break;

                case 'SOS_ALERT':
                    handleSOSAlert(data);
                    break;

                case 'USAGE_UPDATE':
                    handleUsageUpdate(data);
                    break;

                case 'ping':
                    ws.send(JSON.stringify({ type: 'pong' }));
                    break;

                default:
                    console.log(`[WS] 未知消息类型: ${data.type}`);
            }
        } catch (e) {
            console.error('[WS] 消息解析失败:', e.message);
        }
    });

    ws.on('error', (err) => {
        console.error(`[WS] 连接错误 (IP: ${clientIp}):`, err.message);
    });

    ws.on('close', (code, reason) => {
        console.log(`[WS] 连接关闭 (IP: ${clientIp}) code=${code} reason=${reason}`);
        deviceSockets.forEach((socket, deviceId) => {
            if (socket === ws) {
                deviceSockets.delete(deviceId);
                updateDeviceStatus(deviceId, false);
                console.log(`[WS] 设备已断开: ${deviceId}`);
            }
        });
        parentSockets.delete(ws);
    });
});

function handleCommand(data) {
    // 兼容 camelCase (deviceId) 和 snake_case (device_id)
    const deviceId = data.deviceId || data.device_id;
    const command = data.command;

    if (!deviceId) {
        console.log('命令缺少 deviceId，无法转发');
        return;
    }

    const ws = deviceSockets.get(deviceId);
    if (ws && ws.readyState === WebSocket.OPEN) {
        // 转发完整消息（包含 limits/schedules/filters 等数据）
        ws.send(JSON.stringify(data));
        console.log(`发送命令到设备 ${deviceId}: ${command}`);

        if (command === 'LOCK') {
            db.run('UPDATE devices SET is_locked = 1 WHERE device_id = ?', [deviceId]);
        } else if (command === 'UNLOCK') {
            db.run('UPDATE devices SET is_locked = 0 WHERE device_id = ?', [deviceId]);
        } else if (command === 'UPDATE_LIMITS') {
            // 同步限制数据到服务器 DB
            const limits = data.limits;
            if (Array.isArray(limits)) {
                db.run('DELETE FROM app_limits WHERE device_id = ?', [deviceId]);
                const stmt = db.prepare(
                    'INSERT INTO app_limits (device_id, package_name, app_name, daily_limit_minutes, is_limit_enabled, is_whitelist) VALUES (?, ?, ?, ?, ?, ?)'
                );
                limits.forEach(l => {
                    stmt.run([deviceId, l.packageName, l.appName, l.dailyLimitMinutes, l.isLimitEnabled ? 1 : 0, l.isWhitelist ? 1 : 0]);
                });
                stmt.finalize();
            }
        } else if (command === 'UPDATE_SCHEDULES') {
            const schedules = data.schedules;
            if (Array.isArray(schedules)) {
                db.run('DELETE FROM schedules WHERE device_id = ?', [deviceId]);
                const stmt = db.prepare(
                    'INSERT INTO schedules (device_id, name, start_hour, start_minute, end_hour, end_minute, type, enabled) VALUES (?, ?, ?, ?, ?, ?, ?, ?)'
                );
                schedules.forEach(s => {
                    stmt.run([deviceId, s.name, s.startHour, s.startMinute, s.endHour, s.endMinute, s.type, s.enabled ? 1 : 0]);
                });
                stmt.finalize();
            }
        }
    } else {
        console.log(`设备 ${deviceId} 不在线，命令未发送`);
    }
}

function handleSOSAlert(data) {
    const { device_id, device_name, latitude, longitude } = data;

    db.run(
        'INSERT INTO sos_alerts (device_id, device_name, latitude, longitude) VALUES (?, ?, ?, ?)',
        [device_id, device_name, latitude, longitude],
        function(err) {
            if (!err) {
                parentSockets.forEach(parentWs => {
                    if (parentWs.readyState === WebSocket.OPEN) {
                        parentWs.send(JSON.stringify({
                            type: 'SOS_ALERT',
                            id: this.lastID,
                            device_id,
                            device_name,
                            latitude,
                            longitude
                        }));
                    }
                });
                console.log('SOS求助已转发给家长端');
            }
        }
    );
}

function handleUsageUpdate(data) {
    const { device_id, usage_data } = data;
    const today = new Date().toISOString().split('T')[0];

    usage_data.forEach(usage => {
        db.run(
            'INSERT OR REPLACE INTO usage_stats (device_id, package_name, app_name, usage_seconds, date) VALUES (?, ?, ?, ?, ?)',
            [device_id, usage.package_name, usage.app_name, usage.usage_seconds, today]
        );
    });
}

function handleStatusReport(data) {
    const deviceId = data.device_id;
    if (!deviceId) return;

    const battery = data.battery_level ?? data.batteryLevel ?? -1;
    const usageData = data.usage_data || [];

    // 更新内存中的设备信息
    const info = deviceInfoMap.get(deviceId) || {};
    if (battery >= 0) info.battery = battery;
    info.lastReport = Date.now();
    deviceInfoMap.set(deviceId, info);

    // 持久化到 SQLite
    db.run('INSERT OR REPLACE INTO devices (device_id, device_name, bind_code, is_online, battery_level) VALUES (?, ?, ?, 1, ?)',
        [deviceId, info.device_name || '未知设备', data.bind_code || '', battery >= 0 ? battery : 100]);

    // 保存使用数据
    const today = new Date().toISOString().split('T')[0];
    const stmt = db.prepare('INSERT OR REPLACE INTO usage_stats (device_id, package_name, app_name, usage_seconds, date) VALUES (?, ?, ?, ?, ?)');
    usageData.forEach(u => {
        stmt.run([deviceId, u.packageName || u.package_name, u.appName || u.app_name, u.usageSeconds || u.usage_seconds || 0, today]);
    });
    stmt.finalize();

    console.log(`[STATUS] 设备 ${deviceId} 上报: 电量=${battery}%, 使用数据=${usageData.length}条`);

    // 推送状态更新给所有在线家长端
    const statusMsg = JSON.stringify({
        type: 'DEVICE_STATUS',
        deviceId: deviceId,
        device_id: deviceId,
        status: 'online',
        batteryLevel: battery,
        battery_level: battery,
        timestamp: Date.now()
    });
    parentSockets.forEach(pws => {
        if (pws.readyState === WebSocket.OPEN) {
            pws.send(statusMsg);
        }
    });
}

function updateDeviceStatus(deviceId, isOnline) {
    db.run('INSERT OR REPLACE INTO devices (device_id, device_name, is_online) VALUES (?, ?, ?)',
        [deviceId, deviceInfoMap.get(deviceId)?.device_name || '未知设备', isOnline ? 1 : 0]);
}

app.get('/health', (req, res) => {
    res.json({ status: 'ok', uptime: process.uptime() });
});

app.get('/api', (req, res) => {
    res.json({
        message: '守护星服务端运行中',
        version: '1.1.0',
        ws_ready: wss.readyState === 0 ? 'CONNECTING' : wss.readyState === 1 ? 'OPEN' : wss.readyState === 2 ? 'CLOSING' : 'CLOSED',
        online_devices: deviceSockets.size,
        online_parents: parentSockets.size
    });
});

app.post('/api/devices/bind', (req, res) => {
    const { device_id, device_name, bind_code, parent_id } = req.body;

    db.get('SELECT * FROM devices WHERE device_id = ? AND bind_code = ?', [device_id, bind_code], (err, row) => {
        if (err) {
            res.status(500).json({ error: err.message });
        } else if (row) {
            db.run('UPDATE devices SET parent_id = ?, device_name = ? WHERE id = ?', [parent_id, device_name, row.id], (updateErr) => {
                if (updateErr) {
                    res.status(500).json({ error: updateErr.message });
                } else {
                    res.json({ success: true, device_id, message: '绑定成功' });
                }
            });
        } else {
            db.run(
                'INSERT INTO devices (device_id, device_name, bind_code, parent_id) VALUES (?, ?, ?, ?)',
                [device_id, device_name, bind_code, parent_id],
                function(insertErr) {
                    if (insertErr) {
                        res.status(500).json({ error: insertErr.message });
                    } else {
                        res.json({ success: true, id: this.lastID, message: '绑定成功' });
                    }
                }
            );
        }
    });
});

app.get('/api/devices', (req, res) => {
    db.all('SELECT * FROM devices ORDER BY created_at DESC', (err, rows) => {
        if (err) {
            res.status(500).json({ error: err.message });
        } else {
            res.json(rows.map(row => ({
                ...row,
                is_online: deviceSockets.has(row.device_id)
            })));
        }
    });
});

app.get('/api/devices/:deviceId', (req, res) => {
    db.get('SELECT * FROM devices WHERE device_id = ?', [req.params.deviceId], (err, row) => {
        if (err) {
            res.status(500).json({ error: err.message });
        } else if (row) {
            res.json({
                ...row,
                is_online: deviceSockets.has(row.device_id)
            });
        } else {
            res.status(404).json({ error: '设备不存在' });
        }
    });
});

app.post('/api/devices/:deviceId/lock', (req, res) => {
    const deviceId = req.params.deviceId;
    handleCommand({ type: 'COMMAND', device_id: deviceId, command: 'LOCK' });
    res.json({ success: true, message: '锁定命令已发送' });
});

app.post('/api/devices/:deviceId/unlock', (req, res) => {
    const deviceId = req.params.deviceId;
    handleCommand({ type: 'COMMAND', device_id: deviceId, command: 'UNLOCK' });
    res.json({ success: true, message: '解锁命令已发送' });
});

app.get('/api/devices/:deviceId/limits', (req, res) => {
    db.all('SELECT * FROM app_limits WHERE device_id = ?', [req.params.deviceId], (err, rows) => {
        if (err) {
            res.status(500).json({ error: err.message });
        } else {
            res.json(rows);
        }
    });
});

app.post('/api/devices/:deviceId/limits', (req, res) => {
    const { package_name, app_name, daily_limit_minutes, is_whitelist } = req.body;
    const deviceId = req.params.deviceId;

    db.run(
        'INSERT OR REPLACE INTO app_limits (device_id, package_name, app_name, daily_limit_minutes, is_whitelist) VALUES (?, ?, ?, ?, ?)',
        [deviceId, package_name, app_name, daily_limit_minutes, is_whitelist ? 1 : 0],
        function(err) {
            if (err) {
                res.status(500).json({ error: err.message });
            } else {
                const ws = deviceSockets.get(deviceId);
                if (ws && ws.readyState === WebSocket.OPEN) {
                    ws.send(JSON.stringify({
                        type: 'UPDATE_LIMITS'
                    }));
                }
                res.json({ success: true, id: this.lastID });
            }
        }
    );
});

app.delete('/api/devices/:deviceId/limits/:id', (req, res) => {
    db.run('DELETE FROM app_limits WHERE id = ? AND device_id = ?', [req.params.id, req.params.deviceId], (err) => {
        if (err) {
            res.status(500).json({ error: err.message });
        } else {
            res.json({ success: true });
        }
    });
});

app.get('/api/devices/:deviceId/schedules', (req, res) => {
    db.all('SELECT * FROM schedules WHERE device_id = ?', [req.params.deviceId], (err, rows) => {
        if (err) {
            res.status(500).json({ error: err.message });
        } else {
            res.json(rows);
        }
    });
});

app.post('/api/devices/:deviceId/schedules', (req, res) => {
    const { name, start_hour, start_minute, end_hour, end_minute, type, enabled, repeat_days } = req.body;
    const deviceId = req.params.deviceId;

    db.run(
        `INSERT INTO schedules (device_id, name, start_hour, start_minute, end_hour, end_minute, type, enabled, repeat_monday, repeat_tuesday, repeat_wednesday, repeat_thursday, repeat_friday, repeat_saturday, repeat_sunday) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
        [deviceId, name, start_hour, start_minute, end_hour, end_minute, type, enabled ? 1 : 0,
            repeat_days?.monday ? 1 : 0, repeat_days?.tuesday ? 1 : 0, repeat_days?.wednesday ? 1 : 0,
            repeat_days?.thursday ? 1 : 0, repeat_days?.friday ? 1 : 0, repeat_days?.saturday ? 1 : 0, repeat_days?.sunday ? 1 : 0],
        function(err) {
            if (err) {
                res.status(500).json({ error: err.message });
            } else {
                res.json({ success: true, id: this.lastID });
            }
        }
    );
});

app.delete('/api/devices/:deviceId/schedules/:id', (req, res) => {
    db.run('DELETE FROM schedules WHERE id = ? AND device_id = ?', [req.params.id, req.params.deviceId], (err) => {
        if (err) {
            res.status(500).json({ error: err.message });
        } else {
            res.json({ success: true });
        }
    });
});

app.get('/api/sos', (req, res) => {
    db.all('SELECT * FROM sos_alerts ORDER BY created_at DESC LIMIT 50', (err, rows) => {
        if (err) {
            res.status(500).json({ error: err.message });
        } else {
            res.json(rows);
        }
    });
});

app.post('/api/sos/mark-read/:id', (req, res) => {
    db.run('UPDATE sos_alerts SET is_read = 1 WHERE id = ?', [req.params.id], (err) => {
        if (err) {
            res.status(500).json({ error: err.message });
        } else {
            res.json({ success: true });
        }
    });
});

app.get('/api/devices/:deviceId/usage', (req, res) => {
    const { date } = req.query;
    const queryDate = date || new Date().toISOString().split('T')[0];

    db.all('SELECT * FROM usage_stats WHERE device_id = ? AND date = ?', [req.params.deviceId, queryDate], (err, rows) => {
        if (err) {
            res.status(500).json({ error: err.message });
        } else {
            res.json(rows);
        }
    });
});

app.delete('/api/devices/:deviceId', (req, res) => {
    db.run('DELETE FROM devices WHERE device_id = ?', [req.params.deviceId], (err) => {
        if (err) {
            res.status(500).json({ error: err.message });
        } else {
            res.json({ success: true });
        }
    });
});

// === 数据聚合接口 (供家长端仪表盘使用) ===
app.get('/api/devices/:deviceId/dashboard', (req, res) => {
    const deviceId = req.params.deviceId;
    const today = new Date().toISOString().split('T')[0];
    const info = deviceInfoMap.get(deviceId) || {};

    db.serialize(() => {
        // 今日总使用时长
        db.get('SELECT SUM(usage_seconds) as total FROM usage_stats WHERE device_id = ? AND date = ?', [deviceId, today], (err, usageRow) => {
            const totalUsageSeconds = usageRow?.total || 0;
            const totalUsageMin = Math.round(totalUsageSeconds / 60);

            // 限制数量
            db.get('SELECT COUNT(*) as cnt FROM app_limits WHERE device_id = ?', [deviceId], (err, limitRow) => {
                const limitCount = limitRow?.cnt || 0;

                // 超限数量
                db.get('SELECT COUNT(*) as cnt FROM app_limits WHERE device_id = ? AND used_minutes_today >= daily_limit_minutes AND is_limit_enabled = 1', [deviceId], (err, exceededRow) => {
                    res.json({
                        device_id: deviceId,
                        is_online: deviceSockets.has(deviceId),
                        battery_level: info.battery ?? 100,
                        today_usage_minutes: totalUsageMin,
                        limit_count: limitCount,
                        exceeded_count: exceededRow?.cnt || 0
                    });
                });
            });
        });
    });
});

// === 手动触发上报: 服务器向指定儿童设备发送 REPORT_REQUEST ===
app.post('/api/devices/:deviceId/report', (req, res) => {
    const deviceId = req.params.deviceId;
    const ws = deviceSockets.get(deviceId);
    if (ws && ws.readyState === WebSocket.OPEN) {
        ws.send(JSON.stringify({ type: 'REPORT_REQUEST' }));
        console.log(`[API] 手动触发设备 ${deviceId} 上报`);
        res.json({ success: true, message: '已发送上报请求' });
    } else {
        res.json({ success: false, message: '设备不在线' });
    }
});

const PORT = process.env.PORT || 3000;
server.listen(PORT, '0.0.0.0', () => {
    console.log('='.repeat(50));
    console.log(`守护星服务端已启动`);
    console.log(`  HTTP:        http://0.0.0.0:${PORT}`);
    console.log(`  WebSocket:   ws://0.0.0.0:${PORT}`);
    console.log(`  Health:      http://0.0.0.0:${PORT}/health`);
    console.log('='.repeat(50));
});
