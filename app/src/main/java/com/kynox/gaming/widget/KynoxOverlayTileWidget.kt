package com.kynox.gaming.widget

import android.content.Context
import android.widget.RemoteViews
import com.kynox.gaming.R

/** Widget 1x1 "Overlay": satu ketukan menyalakan/mematikan overlay cepat; warna menunjukkan statusnya. */
class KynoxOverlayTileWidget : KynoxWidgetBase() {
    override val layoutRes = R.layout.widget_tile
    override fun rootId() = R.id.wt_root
    override fun clickTargets() = listOf(R.id.wt_root to ACTION_TOGGLE_OVERLAY)

    override fun bind(context: Context, views: RemoteViews, data: WidgetData, fahrenheit: Boolean) {
        val on = data.overlayActive
        views.setInt(R.id.wt_root, "setBackgroundResource", if (on) R.drawable.widget_tile_on else R.drawable.widget_bg)
        views.setTextViewText(R.id.wt_state, if (on) "Aktif" else "Mati")
        views.setTextColor(R.id.wt_state, if (on) 0xFF003736.toInt() else 0xFFE2ECEA.toInt())
        views.setTextColor(R.id.wt_label, if (on) 0xFF00504F.toInt() else 0xFF9DB0AD.toInt())
    }
}
