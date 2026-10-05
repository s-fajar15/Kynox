package com.kynox.gaming.ui.tools

enum class CommandRisk { BLOCKED, RISKY, SAFE }

/** Perintah yang hampir pasti merusak perangkat: ditolak, tidak bisa dijalankan dari sini. */
private val BLOCKED_COMMANDS = listOf(
    Regex("""\brm\s+(-\w+\s+)*-\w*[rR]\w*\s+(-\w+\s+)*/(\s|\*|$)"""),
    Regex("""\bmkfs"""),
    Regex("""\bdd\b.*\bof=/dev/(block|mmcblk|sd|nvme)""")
)

/** Perintah berisiko: boleh, tetapi harus dikonfirmasi dulu. */
private val RISKY_COMMANDS = listOf(
    Regex("""\brm\s"""),
    Regex("""\bdd\b"""),
    Regex("""\b(reboot|poweroff|shutdown)\b"""),
    Regex("""\bsetenforce\b"""),
    Regex("""\b(setprop|resetprop)\b"""),
    Regex("""\bpm\s+(uninstall|clear|disable|disable-user|hide)"""),
    Regex("""\b(chmod|chown|chcon)\b"""),
    Regex("""\b(killall|pkill)\b"""),
    Regex(""">\s*/(sys|proc|dev|data|system|vendor)/"""),
    Regex("""\b(mount|umount|iptables|wipe|format)\b""")
)

/** Menilai perintah Console: ditolak, perlu konfirmasi, atau boleh langsung. Perintah kosong dianggap aman (tidak dijalankan). */
fun classifyCommand(command: String): CommandRisk = when {
    BLOCKED_COMMANDS.any { it.containsMatchIn(command) } -> CommandRisk.BLOCKED
    RISKY_COMMANDS.any { it.containsMatchIn(command) } -> CommandRisk.RISKY
    else -> CommandRisk.SAFE
}
