package com.app.check_inpets.ui.components

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

object TimeFormatting {

    val BRASILIA_ZONE: ZoneId = ZoneOffset.ofHours(-3)

    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
    private val dateTimeFmt = DateTimeFormatter.ofPattern("dd/MM 'às' HH:mm")

    fun formatFriendlyDateTime(epochMillis: Long): String {
        val zone = ZoneId.systemDefault()
        val zoned = Instant.ofEpochMilli(epochMillis).atZone(zone)
        val today = LocalDate.now(zone)
        return when (zoned.toLocalDate()) {
            today -> "Hoje às ${zoned.format(timeFmt)}"
            today.minusDays(1) -> "Ontem às ${zoned.format(timeFmt)}"
            else -> zoned.format(dateTimeFmt)
        }
    }

    fun formatTimeOnly(epochMillis: Long): String {
        val zone = ZoneId.systemDefault()
        return Instant.ofEpochMilli(epochMillis).atZone(zone).format(timeFmt)
    }

    fun isDeviceInBrasiliaZone(nowMillis: Long = System.currentTimeMillis()): Boolean {
        val deviceOffset = ZoneId.systemDefault().rules.getOffset(Instant.ofEpochMilli(nowMillis))
        return deviceOffset == ZoneOffset.ofHours(-3)
    }

    fun convertBrasiliaSlotToDeviceTime(
        brasiliaHHmm: String,
        nowMillis: Long = System.currentTimeMillis()
    ): String {
        val parsed = runCatching { LocalTime.parse(brasiliaHHmm.trim(), timeFmt) }.getOrNull()
            ?: return brasiliaHHmm
        val brasiliaDate = Instant.ofEpochMilli(nowMillis).atZone(BRASILIA_ZONE).toLocalDate()
        val brasiliaZoned = ZonedDateTime.of(brasiliaDate, parsed, BRASILIA_ZONE)
        return brasiliaZoned.withZoneSameInstant(ZoneId.systemDefault()).format(timeFmt)
    }

    fun formatBrasiliaSlotForDevice(
        brasiliaHHmm: String,
        nowMillis: Long = System.currentTimeMillis()
    ): String {
        if (isDeviceInBrasiliaZone(nowMillis)) return brasiliaHHmm
        return convertBrasiliaSlotToDeviceTime(brasiliaHHmm, nowMillis)
    }
}
