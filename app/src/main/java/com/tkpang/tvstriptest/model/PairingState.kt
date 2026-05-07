package com.tkpang.tvstriptest.model

enum class PairingState {
    DETECTED,        // 白 chip — 检测到，未进圈
    PREPARING,       // 绿描边 — 进圈，1.5s 计时中
    QUEUED,          // 灰 chip — 等待队列
    CONNECTING,      // 灰 chip — 正在连接
    PID_MISMATCH,    // 红 chip — PID 不符（永久跳过）
    FAILED,          // 红 chip — 连接/bond 失败（可重试）
    PAIRED,          // 已配对（飞向计数器后从列表移除）
}
