package com.kynox.gaming.domain.model

import com.kynox.gaming.R

/** Nama profil yang ditampilkan ke pengguna (notifikasi, log, dialog). */
fun ProfileType.labelRes(): Int = when (this) {
    ProfileType.BALANCED -> R.string.profile_balanced_label
    ProfileType.PERFORMANCE -> R.string.profile_performance_label
    ProfileType.GAMING -> R.string.profile_gaming_label
    ProfileType.POWERSAVE -> R.string.profile_powersave_label
    ProfileType.CUSTOM -> R.string.profile_custom_label
}
