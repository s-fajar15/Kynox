package com.kynox.gaming.ui

import com.kynox.gaming.domain.model.LogEntry
import com.kynox.gaming.ui.logs.LogFilter
import com.kynox.gaming.ui.logs.filterLogs
import com.kynox.gaming.ui.logs.logActionLabel
import org.junit.Assert.assertEquals
import org.junit.Test

class LogFilteringTest {
    private val logs = listOf(
        LogEntry(1, "APPLY_PROFILE", "cpu", "BALANCED", "GAMING", "SUCCESS", null),
        LogEntry(2, "SET_GOVERNOR", "cpu0", "schedutil", "performance", "FAILED", "Permission denied"),
        LogEntry(3, "CLEAN_RAM", "system", null, null, "SUCCESS", null)
    )

    @Test fun allFilterKeepsEverything() = assertEquals(3, filterLogs(logs, LogFilter.ALL, "").size)

    @Test fun successAndFailedSplitByResult() {
        assertEquals(2, filterLogs(logs, LogFilter.SUCCESS, "").size)
        assertEquals(listOf(2L), filterLogs(logs, LogFilter.FAILED, "").map { it.timestamp })
    }

    @Test fun queryMatchesAnyFieldIgnoringCase() {
        assertEquals(listOf(2L), filterLogs(logs, LogFilter.ALL, "PERMISSION").map { it.timestamp })
        assertEquals(listOf(1L), filterLogs(logs, LogFilter.ALL, "gaming").map { it.timestamp })
        assertEquals(listOf(3L), filterLogs(logs, LogFilter.ALL, "  system ").map { it.timestamp })
    }

    @Test fun queryMatchesTheTranslatedActionName() {
        assertEquals(listOf(3L), filterLogs(logs, LogFilter.ALL, "bersihkan").map { it.timestamp })
    }

    @Test fun filterAndQueryCombine() {
        assertEquals(0, filterLogs(logs, LogFilter.FAILED, "gaming").size)
    }

    @Test fun unknownActionsAreShownAsIs() {
        assertEquals("SOMETHING_NEW", logActionLabel("SOMETHING_NEW"))
        assertEquals("Atur governor CPU", logActionLabel("SET_GOVERNOR"))
    }
}
