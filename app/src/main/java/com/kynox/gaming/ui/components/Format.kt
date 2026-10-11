package com.kynox.gaming.ui.components

import java.util.Locale

/** Format angka bergaya Indonesia (koma desimal, titik ribuan), sama dengan mockup: "5,6 GB", "3.351.824". */
private val ID_LOCALE: Locale = Locale.forLanguageTag("id-ID")

private const val BYTES_PER_GIB = 1024.0 * 1024.0 * 1024.0

fun fmtDec(value: Double, digits: Int = 1): String = String.format(ID_LOCALE, "%." + digits + "f", value)

fun fmtDec(value: Float, digits: Int = 1): String = fmtDec(value.toDouble(), digits)

/** Bilangan bulat dengan pemisah ribuan titik. */
fun fmtInt(value: Long): String = String.format(ID_LOCALE, "%,d", value)

fun fmtInt(value: Int): String = fmtInt(value.toLong())

/** Byte ke GB (basis 1024) dengan [digits] angka desimal, tanpa satuan. */
fun fmtGbValue(bytes: Long, digits: Int = 1): String = fmtDec(bytes / BYTES_PER_GIB, digits)

/** Persentase bulat, mis. "38%". Null menjadi "--". */
fun fmtPercent(value: Float?): String = value?.let { String.format(ID_LOCALE, "%.0f%%", it) } ?: "--"
