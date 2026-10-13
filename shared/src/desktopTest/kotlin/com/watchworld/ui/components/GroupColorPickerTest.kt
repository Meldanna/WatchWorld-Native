package com.watchworld.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 分组取色板的颜色数学（用户 2026-10-13 新增色环取色板）。
 *
 * 只测纯函数：hex ↔ HSV 往返 + 六角色相/明度端点。UI（色环绘制、拖拽取色）不在单测范围，
 * 靠编译与人工目视。
 */
class GroupColorPickerTest {

    @Test
    fun presetColorsRoundTripThroughHsv() {
        for (hex in GROUP_PRESET_COLORS) {
            val (h, s, v) = hexToHsv(hex)
            assertEquals(hex, hsvToHex(h, s, v), "$hex 经 HSV 往返后必须原样回来")
        }
    }

    @Test
    fun hexToHsvHandlesPureHues() {
        val red = hexToHsv("#ff0000")
        assertEquals(0f, red.first, 0.01f)
        assertEquals(1f, red.second, 0.001f)
        assertEquals(1f, red.third, 0.001f)

        val green = hexToHsv("#00ff00")
        assertEquals(120f, green.first, 0.01f)

        val blue = hexToHsv("#0000ff")
        assertEquals(240f, blue.first, 0.01f)

        // 灰度：饱和度为 0，色相无所谓
        assertEquals(0f, hexToHsv("#808080").second, 0.001f)
    }

    @Test
    fun hsvToHexCoversEndpoints() {
        assertEquals("#ff0000", hsvToHex(0f, 1f, 1f))
        assertEquals("#00ff00", hsvToHex(120f, 1f, 1f))
        assertEquals("#0000ff", hsvToHex(240f, 1f, 1f))
        assertEquals("#ffffff", hsvToHex(0f, 0f, 1f))
        assertEquals("#000000", hsvToHex(0f, 0f, 0f))
        // 色相环是 360° 闭合的：360 与 0 同色，负角度也要归一
        assertEquals("#ff0000", hsvToHex(360f, 1f, 1f))
        assertEquals("#ff0000", hsvToHex(-360f, 1f, 1f))
    }

    @Test
    fun randomGroupColorComesFromPalette() {
        repeat(20) {
            assertTrue(randomGroupColor() in GROUP_PRESET_COLORS, "随机默认色必须取自预设色板")
        }
    }
}
