<p align="center"><img src="logo.png" width="280"/></p>

# KHARON MESSENGER
```
 ██╗  ██╗██╗  ██╗ █████╗ ██████╗  ██████╗ ███╗  ██╗
 ██║ ██╔╝██║  ██║██╔══██╗██╔══██╗██╔═══██╗████╗ ██║
 █████╔╝ ███████║███████║██████╔╝██║   ██║██╔██╗██║
 ██╔═██╗ ██╔══██║██╔══██║██╔══██╗██║   ██║██║╚████║
 ██║  ██╗██║  ██║██║  ██║██║  ██║╚██████╔╝██║ ╚███║
 ╚═╝  ╚═╝╚═╝  ╚═╝╚═╝  ╚═╝╚═╝  ╚═╝ ╚═════╝ ╚═╝  ╚══╝
 ──────────────────── MESSENGER v1.0 ─────────────────
```

[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Android-12%2B-brightgreen)](android/)
[![F-Droid](https://img.shields.io/badge/F--Droid-可用-blue)](https://webdotg.github.io/kharon-fdroid/repo)
[![Self-hosted](https://img.shields.io/badge/服务器-自托管-orange)](server/)

> 短暂。加密。零知识。自托管。

**其他语言版本：** [Русский](README.md) · [English](README.en.md) · [العربية](README.ar.md)

---

## 这是什么？

Kharon 是一款为小型团队设计的极简加密通讯工具。消息存活 2 分钟后永久消失。没有账号、没有手机号、没有消息历史、没有别人的服务器。

以希腊神话中的冥界摆渡人卡戎命名——渡过河流的东西不会回来。

---

## 通过 F-Droid 安装

1. 安装 [F-Droid](https://f-droid.org)
2. 设置 → 软件源 → **+**
3. 添加：`https://webdotg.github.io/kharon-fdroid/repo`
4. 搜索 **Kharon Messenger** → 安装

---

## 设计理念

大多数通讯软件将你的消息存储在别人的服务器上，知道你是谁，并可能被迫向第三方提交你的数据。Kharon 建立在完全相反的原则上：

- 服务器是**哑中继** — 只转发加密数据块，不了解其他任何信息
- **你运行服务器** — 在你自己的硬件上
- 消息 **2分钟后自毁** — 无历史记录是设计原则
- **无账号** — 你的身份就是一对密钥，仅此而已
- 添加联系人 = 直接交换公钥，离线或复制粘贴

---

## 消息传输流程

```
[你的手机]
  1. 输入消息
  2. 用收件人公钥加密（X25519 + XSalsa20-Poly1305）
  3. 随机24字节随机数前置到密文
  4. 通过 WSS 发送 base64 数据块

       ↓  TLS 1.3  ↓

[Nginx]
  5. TLS 终止、速率限制、WebSocket 验证

[Node.js 中继]
  6. 接收加密数据块 — 从不查看明文
  7. 路由到收件人的 WebSocket 连接
  8. 离线时：在内存队列中存储（最长2分钟TTL）

       ↓  TLS 1.3  ↓

[收件人手机]
  9. 接收数据块，用自己的私钥解密
  10. 显示明文 — 仅存储在内存中，从不写入磁盘
```

---

## 安全架构

### 密码学

| 用途 | 算法 | 原因 |
|---|---|---|
| 密钥交换 | X25519（Curve25519 DH） | 快速、安全、广泛审计 |
| 加密 | XSalsa20-Poly1305 | 认证加密 — 检测篡改 |
| 随机数 | 每条消息24个随机字节 | 192位熵，碰撞概率≈0 |
| 密钥存储 | Android Keystore + EncryptedSharedPreferences | 硬件支持，无法提取 |
| 联系人存储 | Room + SQLCipher | 静态加密数据库 |

### 服务器知道什么

| 数据 | 服务器可见？ |
|---|---|
| 消息内容 | ❌ 从不 — 只有加密数据块 |
| 发件人身份 | ❌ 只有公钥（44位base64） |
| 消息历史 | ❌ TTL 2分钟，之后从内存删除 |
| 你的姓名 | ❌ 无需注册 |
| 你的手机号 | ❌ 不需要 |
| 你的IP地址 | ⚠️ 是，在连接日志中 |

### 防护层

**服务器：** 仅TLS 1.3 · 速率限制 · 连接限制 · hello超时 · 令牌桶 · MAX_CLIENTS上限 · 负载验证

**客户端：** 仅WSS · 证书固定 · 指数退避 · FLAG_SECURE · 禁止备份 · root检测 · 重放攻击去重

---

## Kharon 不能防护的情况

- **物理设备被没收** — 当前会话在内存中；过去的会话已经消失
- **服务器运营商被入侵** — 他们可以看到连接元数据，但看不到消息内容
- **设备已 root** — Android Keystore 可能被绕过
- **流量分析** — 观察者可以看到你连接到服务器，但不知道你说了什么
- **终端恶意软件** — 没有任何通讯软件能防止屏幕截图恶意软件

---

## 服务器部署

### 要求
- Docker + docker-compose
- 域名或静态IP
- TLS证书（Let's Encrypt）

### 快速开始

```bash
git clone https://github.com/webdotG/kharon-mess
cd kharon-mess/server

cp .env.example .env

# 获取TLS证书
certbot certonly --standalone -d yourdomain.com
mkdir certs
cp /etc/letsencrypt/live/yourdomain.com/fullchain.pem certs/
cp /etc/letsencrypt/live/yourdomain.com/privkey.pem certs/

docker compose up -d
```

---

## 路线图

- [ ] 推送通知（FCM / UnifiedPush）
- [ ] iOS 客户端
- [ ] 前向保密（Double Ratchet）
- [ ] 群聊
- [ ] 语音消息
- [ ] TTL前删除消息
- [ ] 置顶消息（最长24小时）
- [ ] 服务器联邦
- [ ] Tor/I2P 支持
- [ ] 可重现构建
- [ ] GitHub Actions CI

---

## 贡献

参见 [CONTRIBUTING.md](CONTRIBUTING.md)，欢迎 Pull Request。

安全漏洞请参见 [SECURITY.md](SECURITY.md) — **不要**开公开 issue。

---

## 许可证

MIT — 运行你自己的实例，fork，贡献代码。

---

## 为什么叫"Kharon"？

卡戎（Χάρων）是冥府的摆渡人，负责将亡魂渡过冥河斯提克斯和阿刻戎河。他接过给的东西，将其运到对岸，就这样结束了。没有记录，没有回头路。

这就是这款通讯软件所做的事情。
