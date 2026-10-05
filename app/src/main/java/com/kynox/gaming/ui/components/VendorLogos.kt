package com.kynox.gaming.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

enum class MarkFont { SANS, SERIF, MONO }

/**
 * Identitas visual satu merek HP atau satu vendor chipset.
 *
 * Kynox tidak membawa logo resmi (itu merek dagang pemiliknya). Logonya digambar sendiri: nama merek ditulis
 * dengan tipografi khas di atas ilustrasi orisinal (punggung HP dengan kamera, atau kemasan chip dengan pin).
 * Bila file logo asli ditaruh di `res/drawable/` dengan nama `brand_<key>` atau `chip_<key>`, file itu dipakai otomatis.
 */
data class VendorStyle(
    val kind: String,
    val key: String,
    val monogram: String,
    val color: Color,
    val onColor: Color = Color.White,
    val wordmark: String = monogram,
    val weight: FontWeight = FontWeight.Bold,
    val spacingEm: Float = 0f,
    val italic: Boolean = false,
    val font: MarkFont = MarkFont.SANS,
    val upper: Boolean = true
) {
    val drawableName: String get() = "${kind}_$key"
}

private class VendorRule(val style: VendorStyle, val aliases: List<String>, val patterns: List<Regex> = emptyList())

private fun brandRule(
    key: String, mono: String, color: Long, aliases: List<String>, word: String,
    weight: FontWeight = FontWeight.Bold, spacing: Float = 0f, italic: Boolean = false,
    font: MarkFont = MarkFont.SANS, dark: Boolean = false
): VendorRule {
    val upper = word == word.uppercase()
    return VendorRule(
        VendorStyle("brand", key, mono, Color(color), if (dark) Color(0xFF111111) else Color.White, word, weight, spacing, italic, font, upper),
        aliases
    )
}

private fun chipRule(
    key: String, mono: String, color: Long, aliases: List<String>, word: String,
    patterns: List<String> = emptyList(), weight: FontWeight = FontWeight.Bold, spacing: Float = 0f,
    font: MarkFont = MarkFont.SANS, dark: Boolean = false
): VendorRule {
    val upper = word == word.uppercase()
    return VendorRule(
        VendorStyle("chip", key, mono, Color(color), if (dark) Color(0xFF111111) else Color.White, word, weight, spacing, false, font, upper),
        aliases,
        patterns.map { Regex(it) }
    )
}

object VendorCatalog {

    // Urutan penting: merek yang lebih spesifik (poco, iqoo, redmagic) harus dicek sebelum induknya.
    private val brands = listOf(
        brandRule("poco", "P", 0xFFFFD500, listOf("poco"), "POCO", FontWeight.Black, 0.06f, dark = true),
        brandRule("iqoo", "iQ", 0xFF1F2937, listOf("iqoo"), "iQOO", FontWeight.Bold, 0.04f),
        brandRule("xiaomi", "mi", 0xFFFF6900, listOf("xiaomi", "redmi", "miui"), "xiaomi", FontWeight.Medium, 0.02f),
        brandRule("samsung", "S", 0xFF1428A0, listOf("samsung"), "SAMSUNG", FontWeight.Black, 0.12f),
        brandRule("realme", "R", 0xFFFFC915, listOf("realme"), "realme", FontWeight.Bold, 0f, dark = true),
        brandRule("oneplus", "1+", 0xFFF5010C, listOf("oneplus"), "ONEPLUS", FontWeight.Bold, 0.14f),
        brandRule("oppo", "O", 0xFF1E9E5A, listOf("oppo"), "OPPO", FontWeight.Black, 0.1f),
        brandRule("vivo", "V", 0xFF415FFF, listOf("vivo"), "vivo", FontWeight.Medium, 0.02f, italic = true),
        brandRule("google", "G", 0xFF4285F4, listOf("google", "pixel"), "Google", FontWeight.Medium, 0f),
        brandRule("motorola", "M", 0xFF005EB8, listOf("motorola", "moto"), "motorola", FontWeight.Bold, 0f),
        brandRule("nothing", "N", 0xFF2B2B2B, listOf("nothing"), "NOTHING", FontWeight.Medium, 0.14f, font = MarkFont.MONO),
        brandRule("asus", "A", 0xFF00539B, listOf("asus", "rog"), "ASUS", FontWeight.Black, 0.16f),
        brandRule("infinix", "i", 0xFF2BB673, listOf("infinix"), "Infinix", FontWeight.Bold, 0f),
        brandRule("tecno", "T", 0xFF0A66FF, listOf("tecno"), "TECNO", FontWeight.Bold, 0.1f),
        brandRule("itel", "it", 0xFFE4002B, listOf("itel"), "itel", FontWeight.Black, 0f, italic = true),
        brandRule("huawei", "H", 0xFFCF0A2C, listOf("huawei"), "HUAWEI", FontWeight.Medium, 0.14f),
        brandRule("honor", "H", 0xFF1F6BFF, listOf("honor"), "HONOR", FontWeight.Bold, 0.16f),
        brandRule("sony", "S", 0xFF2B2B2B, listOf("sony"), "SONY", FontWeight.Bold, 0.18f, font = MarkFont.SERIF),
        brandRule("lg", "LG", 0xFFA50034, listOf("lge", "lg"), "LG", FontWeight.Black, 0.1f),
        brandRule("nokia", "N", 0xFF124191, listOf("nokia", "hmd"), "NOKIA", FontWeight.Bold, 0.16f),
        brandRule("zte", "Z", 0xFF0079C1, listOf("zte", "nubia", "redmagic"), "ZTE", FontWeight.Black, 0.12f),
        brandRule("lenovo", "L", 0xFFE2231A, listOf("lenovo"), "Lenovo", FontWeight.Bold, 0f),
        brandRule("meizu", "M", 0xFF0AA6E8, listOf("meizu"), "MEIZU", FontWeight.Medium, 0.16f)
    )

    // Unisoc dicek sebelum Qualcomm karena pola "sc####" dipakai keduanya.
    private val chips = listOf(
        chipRule(
            "unisoc", "U", 0xFFFF6A13, listOf("unisoc", "spreadtrum", "tiger", "ums"), "UNISOC",
            listOf("\\bsc9\\d{3}", "\\bsc7\\d{3}", "\\bsc85\\d{2}", "\\bt6\\d{2}\\b", "\\bt7\\d{2}\\b", "\\bt8\\d{2}\\b"),
            FontWeight.Bold, 0.1f
        ),
        chipRule("mediatek", "MT", 0xFFFFC800, listOf("mediatek", "dimensity", "helio", "mtk"), "MEDIATEK", listOf("\\bmt\\d{4}"), FontWeight.Black, 0.06f, dark = true),
        chipRule("exynos", "E", 0xFF1428A0, listOf("exynos", "samsung", "universal", "s5e"), "EXYNOS", listOf("\\bs5e\\d+"), FontWeight.Bold, 0.14f),
        chipRule("tensor", "G", 0xFF4285F4, listOf("tensor", "gs101", "gs201", "zuma"), "Tensor", listOf("\\bgs\\d{3}"), FontWeight.Medium, 0f),
        chipRule("kirin", "K", 0xFFCF0A2C, listOf("kirin", "hisilicon"), "KIRIN", listOf("\\bhi\\d{4}"), FontWeight.Bold, 0.16f),
        chipRule(
            "qualcomm", "Q", 0xFF3253DC,
            listOf("qualcomm", "qti", "snapdragon", "adreno", "kona", "lahaina", "taro", "kalama", "pineapple", "waipio", "msmnile", "sdm", "msm", "lito", "atoll", "trinket", "bengal", "holi", "cape", "crow", "parrot", "monaco", "sun"),
            "Snapdragon",
            listOf("\\bsm\\d{4}", "\\bsdm\\d{3}", "\\bmsm\\d{4}", "\\bapq\\d{4}", "\\bsc\\d{4}", "\\bqcm\\d{4}"),
            FontWeight.Bold, 0f
        )
    )

    private val neutral = Color(0xFF475569)

    /** Gaya merek HP dari Build.BRAND/MANUFACTURER. Merek diperiksa lebih dulu karena POCO melapor manufaktur "Xiaomi". */
    fun brand(manufacturer: String?, brand: String?): VendorStyle {
        val brandText = brand.orEmpty().lowercase().trim()
        val makerText = manufacturer.orEmpty().lowercase().trim()
        for (text in listOf(brandText, makerText)) {
            if (text.isEmpty()) continue
            brands.firstOrNull { rule -> rule.aliases.any { text == it || text.contains(it) } }?.let { return it.style }
        }
        val label = (brand?.takeIf { it.isNotBlank() } ?: manufacturer.orEmpty()).trim()
        return VendorStyle("brand", "generic", label.take(1).uppercase().ifEmpty { "?" }, neutral, wordmark = label.uppercase().ifEmpty { "?" }, spacingEm = 0.08f)
    }

    /** Gaya vendor chipset dari nama SoC; GPU dipakai sebagai petunjuk terakhir (Adreno = Qualcomm). */
    fun chipset(socManufacturer: String?, socModel: String?, gpu: String? = null): VendorStyle {
        val soc = "${socManufacturer.orEmpty()} ${socModel.orEmpty()}".lowercase().trim()
        for (text in listOf(soc, gpu.orEmpty().lowercase())) {
            if (text.isBlank()) continue
            chips.firstOrNull { rule ->
                rule.patterns.any { it.containsMatchIn(text) } ||
                    rule.aliases.any { alias -> if (alias.length <= 3) Regex("\\b${Regex.escape(alias)}").containsMatchIn(text) else text.contains(alias) }
            }?.let { return it.style }
        }
        val label = (socManufacturer?.takeIf { it.isNotBlank() && !it.equals("unknown", true) } ?: socModel.orEmpty()).trim()
        return VendorStyle("chip", "generic", label.take(1).uppercase().ifEmpty { "?" }, neutral, wordmark = label.uppercase().ifEmpty { "?" }, spacingEm = 0.08f)
    }
}

private fun VendorStyle.fontFamily(): FontFamily = when (font) {
    MarkFont.SANS -> FontFamily.SansSerif
    MarkFont.SERIF -> FontFamily.Serif
    MarkFont.MONO -> FontFamily.Monospace
}

/**
 * Logo lebar: nama merek bertipografi khas di atas ilustrasi orisinal.
 * Merek HP = punggung HP dengan jalur kamera; chipset = kemasan chip dengan pin. Tanpa gradient atau glow.
 */
@Composable
fun VendorLogoTile(style: VendorStyle, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val resId = remember(style.drawableName) {
        context.resources.getIdentifier(style.drawableName, "drawable", context.packageName)
    }
    val isChip = style.kind == "chip"
    BoxWithConstraints(
        modifier.clip(RoundedCornerShape(14.dp)).background(if (resId != 0) Color.White else style.color)
    ) {
        val w = maxWidth
        val h = maxHeight
        if (resId != 0) {
            Image(
                painterResource(resId), contentDescription = null, contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(h * 0.18f)
            )
        } else {
            val islandW = if (isChip) 0.dp else h * 0.62f
            val sidePad = if (isChip) h * 0.34f else h * 0.14f
            val available = (w - islandW - sidePad * 2).value.coerceAtLeast(20f)
            val base = if (style.upper) 0.70f else 0.60f
            val fontSize = (available / (style.wordmark.length.coerceAtLeast(1) * (base + style.spacingEm)))
                .coerceIn(9f, h.value * 0.40f)

            Canvas(Modifier.fillMaxSize()) {
                val ph = size.height
                val pw = size.width
                val stroke = 1.5.dp.toPx()
                if (isChip) {
                    val pad = ph * 0.22f
                    drawRoundRect(
                        color = style.onColor,
                        topLeft = Offset(pad, pad),
                        size = Size(pw - 2 * pad, ph - 2 * pad),
                        cornerRadius = CornerRadius(ph * 0.08f),
                        style = Stroke(stroke)
                    )
                    val pins = (((pw - 2 * pad) / (ph * 0.16f)).toInt()).coerceIn(5, 14)
                    val gap = (pw - 2 * pad) / (pins + 1)
                    for (i in 1..pins) {
                        val x = pad + gap * i
                        drawLine(style.onColor, Offset(x, pad * 0.35f), Offset(x, pad), stroke)
                        drawLine(style.onColor, Offset(x, ph - pad), Offset(x, ph - pad * 0.35f), stroke)
                    }
                    drawCircle(style.onColor, radius = ph * 0.035f, center = Offset(pad + ph * 0.1f, pad + ph * 0.1f))
                } else {
                    val body = lerp(style.color, Color.Black, 0.22f)
                    val lens = lerp(style.color, Color.Black, 0.5f)
                    val ix = ph * 0.14f
                    val iy = ph * 0.2f
                    val iw = ph * 0.42f
                    val ih = ph * 0.6f
                    drawRoundRect(body, Offset(ix, iy), Size(iw, ih), CornerRadius(iw / 2f))
                    drawCircle(lens, radius = iw * 0.3f, center = Offset(ix + iw / 2f, iy + ih * 0.3f))
                    drawCircle(lens, radius = iw * 0.3f, center = Offset(ix + iw / 2f, iy + ih * 0.7f))
                }
            }
            Box(
                Modifier.fillMaxSize().padding(start = islandW + sidePad, end = sidePad),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    style.wordmark,
                    color = style.onColor,
                    fontSize = fontSize.sp,
                    fontWeight = style.weight,
                    fontStyle = if (style.italic) FontStyle.Italic else FontStyle.Normal,
                    fontFamily = style.fontFamily(),
                    letterSpacing = style.spacingEm.em,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

/** Lencana persegi berisi monogram (atau logo asli bila ada), untuk ruang sempit. */
@Composable
fun VendorBadge(style: VendorStyle, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    val context = LocalContext.current
    val resId = remember(style.drawableName) {
        context.resources.getIdentifier(style.drawableName, "drawable", context.packageName)
    }
    val shape = RoundedCornerShape(size * 0.28f)
    if (resId != 0) {
        Box(modifier.size(size).clip(shape).background(Color.White).padding(size * 0.14f), contentAlignment = Alignment.Center) {
            Image(painterResource(resId), contentDescription = null, contentScale = ContentScale.Fit)
        }
    } else {
        val fontSize: TextUnit = when (style.monogram.length) {
            1 -> (size.value * 0.46f).sp
            2 -> (size.value * 0.38f).sp
            else -> (size.value * 0.30f).sp
        }
        Box(modifier.size(size).clip(shape).background(style.color), contentAlignment = Alignment.Center) {
            Text(style.monogram, color = style.onColor, fontSize = fontSize, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}
