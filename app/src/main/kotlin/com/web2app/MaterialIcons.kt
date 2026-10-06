package com.web2app

/**
 * The catalog of built-in Material icons users can assign to bottom-tab items.
 *
 * A tab icon is stored as a string. Material icons use the form `material:<key>`
 * (e.g. `material:home`); any other non-blank value is treated as a Base64 image
 * data-URI for backward compatibility. Keeping the catalog in one place means the
 * builder picker and the runtime bottom nav resolve the same keys.
 */
object MaterialIcons {

    const val PREFIX = "material:"

    /** Ordered key → drawable resource. Insertion order drives the picker grid. */
    val ICONS: LinkedHashMap<String, Int> = linkedMapOf(
        "home" to R.drawable.ic_mt_home,
        "search" to R.drawable.ic_mt_search,
        "explore" to R.drawable.ic_mt_explore,
        "person" to R.drawable.ic_mt_person,
        "account" to R.drawable.ic_mt_account,
        "settings" to R.drawable.ic_mt_settings,
        "favorite" to R.drawable.ic_mt_favorite,
        "star" to R.drawable.ic_mt_star,
        "bookmark" to R.drawable.ic_mt_bookmark,
        "notifications" to R.drawable.ic_mt_notifications,
        "mail" to R.drawable.ic_mt_mail,
        "chat" to R.drawable.ic_mt_chat,
        "phone" to R.drawable.ic_mt_phone,
        "cart" to R.drawable.ic_mt_cart,
        "bag" to R.drawable.ic_mt_bag,
        "location" to R.drawable.ic_mt_location,
        "calendar" to R.drawable.ic_mt_calendar,
        "apps" to R.drawable.ic_mt_apps,
        "play" to R.drawable.ic_mt_play,
        "info" to R.drawable.ic_mt_info,
        "menu" to R.drawable.ic_mt_menu
    )

    /** True when [icon] references a built-in Material icon (vs. a Base64 image). */
    fun isMaterial(icon: String): Boolean = icon.startsWith(PREFIX)

    /** The full stored value for a catalog [key], e.g. "home" → "material:home". */
    fun valueFor(key: String): String = PREFIX + key

    /** The drawable resource for a stored Material icon value, or null if unknown. */
    fun resFor(icon: String): Int? =
        if (isMaterial(icon)) ICONS[icon.removePrefix(PREFIX)] else null
}
