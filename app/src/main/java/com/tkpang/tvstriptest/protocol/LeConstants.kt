package com.tkpang.tvstriptest.protocol

import java.util.UUID

object LeConstants {
    val SERVICE_UUID: UUID = UUID.fromString("1e2aa501-7292-4263-a8f1-be907f039a1f")
    val WRITE_UUID: UUID = UUID.fromString("1e2aa502-7292-4263-a8f1-be907f039a1f")
    val NOTIFY_UUID: UUID = UUID.fromString("1e2aa503-7292-4263-a8f1-be907f039a1f")
    const val ADV_NAME = "LP"

    const val LE_CMD_DEV_INFO_GET = 0x1000
    const val LE_CMD_DEV_INFO_GETR = 0x1001
    const val LE_CMD_BOND = 0x1002
    const val LE_CMD_BONDR = 0x1003
    const val LE_CMD_UNBOND = 0x1004
    const val LE_CMD_UNBONDR = 0x1005
    const val LE_CMD_DP_PRP_SET = 0x1100
    const val LE_CMD_DP_PRP_SETR = 0x1101
    const val LE_CMD_DEV_INFO_GET_MARK = 0x5a5aa5a5

    const val LE_CODE_SUCCESS = 0x0000
    const val LE_CODE_BONDED = 0x000a

    const val HDR_VER = 0x5a
    const val CTRL_NONE = 0x50
}
