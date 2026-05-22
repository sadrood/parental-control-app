/**
 * WebSocket 连接诊断脚本
 * 在电脑上运行: node test_ws.js
 */
const WebSocket = require('ws');

const URL = 'ws://139.9.176.191:3000';

console.log('=== 守护星 WebSocket 诊断 ===\n');

// 1. 先测试 HTTP 连通性
console.log('1. 测试 HTTP 端点...');
const http = require('http');
http.get('http://139.9.176.191:3000/api', (res) => {
  let data = '';
  res.on('data', chunk => data += chunk);
  res.on('end', () => {
    console.log('   HTTP 响应:', res.statusCode, res.statusMessage);
    console.log('   Body:', data);
    console.log('   HTTP 服务正常 ✓\n');
    testWebSocket();
  });
}).on('error', (e) => {
  console.log('   HTTP 连接失败:', e.message);
  console.log('   服务器可能未运行或端口不通 ✗\n');
  process.exit(1);
});

function testWebSocket() {
  console.log('2. 测试 WebSocket 连接...');
  const ws = new WebSocket(URL);

  ws.on('open', () => {
    console.log('   WebSocket 连接成功 ✓');
    ws.send(JSON.stringify({ type: 'ping' }));
  });

  ws.on('message', (data) => {
    console.log('   收到消息:', data.toString());
    ws.close();
  });

  ws.on('error', (err) => {
    console.log('   WebSocket 错误:', err.message);
  });

  ws.on('close', (code, reason) => {
    console.log('   连接关闭: code=' + code + ', reason=' + reason.toString());
    if (code === 1006) {
      console.log('   诊断: 服务器不支持 WebSocket 升级 (可能缺 ws 模块或未重启)');
    } else if (code === 4000) {
      console.log('   诊断: 自定义错误码');
    }
    process.exit(code === 1000 ? 0 : 1);
  });

  // 5秒超时
  setTimeout(() => {
    if (ws.readyState !== WebSocket.OPEN) {
      console.log('   超时：5秒内未建立连接 ✗');
      ws.terminate();
      process.exit(1);
    }
  }, 5000);
}
