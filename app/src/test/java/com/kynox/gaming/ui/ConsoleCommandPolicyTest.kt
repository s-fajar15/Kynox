package com.kynox.gaming.ui

import com.kynox.gaming.ui.tools.CommandRisk
import com.kynox.gaming.ui.tools.classifyCommand
import org.junit.Assert.assertEquals
import org.junit.Test

class ConsoleCommandPolicyTest {

    @Test
    fun destructiveCommandsAreBlocked() {
        assertEquals(CommandRisk.BLOCKED, classifyCommand("rm -rf /"))
        assertEquals(CommandRisk.BLOCKED, classifyCommand("rm -rf /*"))
        assertEquals(CommandRisk.BLOCKED, classifyCommand("mkfs.ext4 /dev/block/sda1"))
        assertEquals(CommandRisk.BLOCKED, classifyCommand("dd if=/dev/zero of=/dev/block/mmcblk0"))
    }

    @Test
    fun riskyCommandsNeedConfirmation() {
        assertEquals(CommandRisk.RISKY, classifyCommand("rm -rf /sdcard/Download/x"))
        assertEquals(CommandRisk.RISKY, classifyCommand("reboot"))
        assertEquals(CommandRisk.RISKY, classifyCommand("setenforce 0"))
        assertEquals(CommandRisk.RISKY, classifyCommand("pm uninstall com.example"))
        assertEquals(CommandRisk.RISKY, classifyCommand("echo 1 > /sys/class/power_supply/battery/x"))
        assertEquals(CommandRisk.RISKY, classifyCommand("setprop persist.x 1"))
    }

    @Test
    fun readOnlyCommandsRunDirectly() {
        assertEquals(CommandRisk.SAFE, classifyCommand("ls /sdcard"))
        assertEquals(CommandRisk.SAFE, classifyCommand("getprop ro.product.model"))
        assertEquals(CommandRisk.SAFE, classifyCommand("cat /proc/cpuinfo"))
        assertEquals(CommandRisk.SAFE, classifyCommand("dumpsys battery"))
    }
}
