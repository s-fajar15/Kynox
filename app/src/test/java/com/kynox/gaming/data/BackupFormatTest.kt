package com.kynox.gaming.data

import com.kynox.gaming.data.backup.BACKUP_VERSION
import com.kynox.gaming.data.backup.BackupFile
import com.kynox.gaming.data.backup.BackupFormat
import com.kynox.gaming.data.backup.BackupParse
import com.kynox.gaming.data.settings.AppSettings
import com.kynox.gaming.data.settings.PortableSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupFormatTest {

    private fun file() = BackupFile(
        BACKUP_VERSION, "2.3.0", 1234L,
        PortableSettings.export(AppSettings()),
        mapOf(
            "managed_games" to mapOf("packages" to "com.a,com.b"),
            "automation_rule_r1" to mapOf("package" to "com.a", "enabled" to "true", "refresh" to null)
        )
    )

    @Test fun roundTrip() {
        val parsed = BackupFormat.parse(BackupFormat.serialize(file())) as BackupParse.Ok
        assertEquals(0, parsed.skippedKeys)
        assertEquals("com.a,com.b", parsed.file.stores["managed_games"]!!["packages"])
        assertEquals(null, parsed.file.stores["automation_rule_r1"]!!["refresh"])
        assertEquals("2.3.0", parsed.file.appVersion)
    }

    @Test fun rejectsNonJsonAndForeignFiles() {
        assertTrue(BackupFormat.parse("hello") is BackupParse.Rejected)
        assertTrue(BackupFormat.parse("{\"format\":\"other\",\"version\":1}") is BackupParse.Rejected)
        assertTrue(BackupFormat.parse("{\"format\":\"kynox-backup\"}") is BackupParse.Rejected)
    }

    @Test fun rejectsNewerVersion() {
        val text = "{\"format\":\"kynox-backup\",\"version\":${BACKUP_VERSION + 1}}"
        val result = BackupFormat.parse(text)
        assertTrue(result is BackupParse.Rejected)
        assertTrue((result as BackupParse.Rejected).reason.contains("lebih baru"))
    }

    @Test fun deviceStateAndUnknownKeysAreSkipped() {
        val text = """{"format":"kynox-backup","version":1,"stores":{
            "profile_original_state":{"x":"1"},
            "game_mode":{"previous_profile":"GAMING"},
            "automation_rule_../evil":{"package":"x"},
            "managed_games":{"packages":"com.a"}}}"""
        val ok = BackupFormat.parse(text) as BackupParse.Ok
        assertEquals(setOf("managed_games"), ok.file.stores.keys)
        assertEquals(3, ok.skippedKeys)
    }

    @Test fun nonStringValuesInAStoreSkipThatStore() {
        val text = """{"format":"kynox-backup","version":1,"stores":{"managed_games":{"packages":5}}}"""
        val ok = BackupFormat.parse(text) as BackupParse.Ok
        assertTrue(ok.file.stores.isEmpty())
        assertEquals(1, ok.skippedKeys)
    }

    @Test fun portableSettingsAreValidatedAndClamped() {
        val clean = PortableSettings.sanitize(
            mapOf(
                "theme_mode" to "LIGHT",
                "temperature_unit" to "KELVIN",
                "refresh_interval_ms" to 999_999,
                "overlay_opacity_percent" to 5,
                "require_confirmation" to "yes",
                "thermal_warn_threshold_c" to 3,
                "history_interval_sec" to 0,
                "notify_cooldown_min" to 15.0,
                "apply_on_boot" to true,
                "safe_mode_active" to false
            )
        )
        assertEquals("LIGHT", clean["theme_mode"])
        assertFalse(clean.containsKey("temperature_unit"))
        assertEquals(5000, clean["refresh_interval_ms"])
        assertEquals(20, clean["overlay_opacity_percent"])
        assertFalse(clean.containsKey("require_confirmation"))
        assertEquals(35, clean["thermal_warn_threshold_c"])
        assertEquals(5, clean["history_interval_sec"])
        assertEquals(15, clean["notify_cooldown_min"])
        assertFalse("boot-related settings must never be imported", clean.containsKey("apply_on_boot"))
        assertFalse(clean.containsKey("safe_mode_active"))
    }
}
