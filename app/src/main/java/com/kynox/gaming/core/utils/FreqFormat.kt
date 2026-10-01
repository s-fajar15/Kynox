package com.kynox.gaming.core.utils

/**
 * GPU frequencies are kept in whatever unit the kernel exposes them in, so
 * values can be written back to sysfs unchanged. kgsl/devfreq report Hz
 * (257000000), some other kernels report kHz (257000). No real GPU runs
 * anywhere near 10 MHz in Hz or 10 GHz in kHz, so the magnitude tells the
 * two apart.
 */
fun gpuFreqMhz(raw: Int): Int = if (raw >= 10_000_000) raw / 1_000_000 else raw / 1000
