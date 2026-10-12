package com.example.livetranslate

import java.util.Locale

data class LanguageItem(
    val code: String,
    val name: String,
    val nativeName: String,
    val flag: String
) {
    fun getLocalizedLabel(uiLang: String = Locale.getDefault().language): String {
        val primaryName = when (uiLang) {
            "fa" -> when (code) {
                "fa" -> "فارسی"
                "en" -> "انگلیسی"
                "de" -> "آلمانی"
                else -> name
            }
            "de" -> when (code) {
                "fa" -> "Persisch"
                "en" -> "Englisch"
                "de" -> "Deutsch"
                else -> name
            }
            else -> name
        }
        return if (primaryName.equals(nativeName, ignoreCase = true)) {
            "$flag  $primaryName ($code)"
        } else {
            "$flag  $primaryName — $nativeName ($code)"
        }
    }

    val displayLabel: String
        get() = getLocalizedLabel()

    override fun toString(): String = getLocalizedLabel()
}

object SupportedLanguages {

    private val pinnedTop = listOf(
        LanguageItem("fa", "Persian", "فارسی", "🇮🇷"),
        LanguageItem("en", "English", "English", "🇬🇧"),
        LanguageItem("de", "German", "Deutsch", "🇩🇪")
    )

    private val others = listOf(
        LanguageItem("af", "Afrikaans", "Afrikaans", "🇿🇦"),
        LanguageItem("sq", "Albanian", "Shqip", "🇦🇱"),
        LanguageItem("am", "Amharic", "አማርኛ", "🇪🇹"),
        LanguageItem("ar", "Arabic", "العربية", "🇸🇦"),
        LanguageItem("hy", "Armenian", "Հայերեն", "🇦🇲"),
        LanguageItem("as", "Assamese", "অসমীয়া", "🇮🇳"),
        LanguageItem("ay", "Aymara", "Aymar aru", "🇧🇴"),
        LanguageItem("az", "Azerbaijani", "Azərbaycan", "🇦🇿"),
        LanguageItem("bm", "Bambara", "Bamanankan", "🇲🇱"),
        LanguageItem("eu", "Basque", "Euskara", "🇪🇸"),
        LanguageItem("be", "Belarusian", "Беларуская", "🇧🇾"),
        LanguageItem("bn", "Bengali", "বাংলা", "🇧🇩"),
        LanguageItem("bho", "Bhojpuri", "भोजपुरी", "🇮🇳"),
        LanguageItem("bs", "Bosnian", "Bosanski", "🇧🇦"),
        LanguageItem("bg", "Bulgarian", "Български", "🇧🇬"),
        LanguageItem("ca", "Catalan", "Català", "🇪🇸"),
        LanguageItem("ceb", "Cebuano", "Cebuano", "🇵🇭"),
        LanguageItem("ny", "Chichewa", "Chichewa", "🇲🇼"),
        LanguageItem("zh-CN", "Chinese (Simplified)", "简体中文", "🇨🇳"),
        LanguageItem("zh-TW", "Chinese (Traditional)", "繁體中文", "🇹🇼"),
        LanguageItem("co", "Corsican", "Corsu", "🇫🇷"),
        LanguageItem("hr", "Croatian", "Hrvatski", "🇭🇷"),
        LanguageItem("cs", "Czech", "Čeština", "🇨🇿"),
        LanguageItem("da", "Danish", "Dansk", "🇩🇰"),
        LanguageItem("dv", "Dhivehi", "ދިވެހި", "🇲🇻"),
        LanguageItem("doi", "Dogri", "डोगरी", "🇮🇳"),
        LanguageItem("nl", "Dutch", "Nederlands", "🇳🇱"),
        LanguageItem("eo", "Esperanto", "Esperanto", "🌍"),
        LanguageItem("et", "Estonian", "Eesti", "🇪🇪"),
        LanguageItem("ee", "Ewe", "Eʋegbe", "🇬🇭"),
        LanguageItem("tl", "Filipino (Tagalog)", "Filipino", "🇵🇭"),
        LanguageItem("fi", "Finnish", "Suomi", "🇫🇮"),
        LanguageItem("fr", "French", "Français", "🇫🇷"),
        LanguageItem("fy", "Frisian", "Frysk", "🇳🇱"),
        LanguageItem("gl", "Galician", "Galego", "🇪🇸"),
        LanguageItem("gn", "Guarani", "Avañe'ẽ", "🇵🇾"),
        LanguageItem("gu", "Gujarati", "ગુજરાતી", "🇮🇳"),
        LanguageItem("ht", "Haitian Creole", "Kreyòl ayisyen", "🇭🇹"),
        LanguageItem("ha", "Hausa", "Hausa", "🇳🇬"),
        LanguageItem("haw", "Hawaiian", "ʻŌlelo Hawaiʻi", "🇺🇸"),
        LanguageItem("iw", "Hebrew", "עברית", "🇮🇱"),
        LanguageItem("hi", "Hindi", "हिन्दी", "🇮🇳"),
        LanguageItem("hmn", "Hmong", "Hmoob", "🌏"),
        LanguageItem("hu", "Hungarian", "Magyar", "🇭🇺"),
        LanguageItem("is", "Icelandic", "Íslenska", "🇮🇸"),
        LanguageItem("ig", "Igbo", "Igbo", "🇳🇬"),
        LanguageItem("ilo", "Ilocano", "Ilokano", "🇵🇭"),
        LanguageItem("id", "Indonesian", "Bahasa Indonesia", "🇮🇩"),
        LanguageItem("ga", "Irish", "Gaeilge", "🇮🇪"),
        LanguageItem("it", "Italian", "Italiano", "🇮🇹"),
        LanguageItem("ja", "Japanese", "日本語", "🇯🇵"),
        LanguageItem("jw", "Javanese", "Basa Jawa", "🇮🇩"),
        LanguageItem("kn", "Kannada", "ಕನ್ನಡ", "🇮🇳"),
        LanguageItem("ka", "Georgian", "ქართული", "🇬🇪"),
        LanguageItem("kk", "Kazakh", "Қазақ тілі", "🇰🇿"),
        LanguageItem("km", "Khmer", "ខ្មែរ", "🇰🇭"),
        LanguageItem("rw", "Kinyarwanda", "Kinyarwanda", "🇷🇼"),
        LanguageItem("gom", "Konkani", "कोंकणी", "🇮🇳"),
        LanguageItem("ko", "Korean", "한국어", "🇰🇷"),
        LanguageItem("kri", "Krio", "Krio", "🇸🇱"),
        LanguageItem("ku", "Kurdish (Kurmanji)", "Kurdî", "🌍"),
        LanguageItem("ckb", "Kurdish (Sorani)", "کوردیی ناوەندی", "🌍"),
        LanguageItem("ky", "Kyrgyz", "Кыргызча", "🇰🇬"),
        LanguageItem("lo", "Lao", "ລາວ", "🇱🇦"),
        LanguageItem("la", "Latin", "Latina", "🇻🇦"),
        LanguageItem("lv", "Latvian", "Latviešu", "🇱🇻"),
        LanguageItem("ln", "Lingala", "Lingála", "🇨🇩"),
        LanguageItem("lt", "Lithuanian", "Lietuvių", "🇱🇹"),
        LanguageItem("lg", "Luganda", "Luganda", "🇺🇬"),
        LanguageItem("lb", "Luxembourgish", "Lëtzebuergesch", "🇱🇺"),
        LanguageItem("mk", "Macedonian", "Македонски", "🇲🇰"),
        LanguageItem("mai", "Maithili", "मैथिली", "🇮🇳"),
        LanguageItem("mg", "Malagasy", "Malagasy", "🇲🇬"),
        LanguageItem("ms", "Malay", "Bahasa Melayu", "🇲🇾"),
        LanguageItem("ml", "Malayalam", "മലയാളം", "🇮🇳"),
        LanguageItem("mt", "Maltese", "Malti", "🇲🇹"),
        LanguageItem("mi", "Maori", "Te Reo Māori", "🇳🇿"),
        LanguageItem("mr", "Marathi", "मराठी", "🇮🇳"),
        LanguageItem("mni-Mtei", "Meiteilon (Manipuri)", "মৈতৈলোন্", "🇮🇳"),
        LanguageItem("lus", "Mizo", "Mizo ṭawng", "🇮🇳"),
        LanguageItem("mn", "Mongolian", "Монгол", "🇲🇳"),
        LanguageItem("my", "Myanmar (Burmese)", "မြန်မာ", "🇲🇲"),
        LanguageItem("ne", "Nepali", "नेपाली", "🇳🇵"),
        LanguageItem("no", "Norwegian", "Norsk", "🇳🇴"),
        LanguageItem("or", "Odia (Oriya)", "ଓଡ଼ିଆ", "🇮🇳"),
        LanguageItem("om", "Oromo", "Afaan Oromoo", "🇪🇹"),
        LanguageItem("ps", "Pashto", "پښتو", "🇦🇫"),
        LanguageItem("pl", "Polish", "Polski", "🇵🇱"),
        LanguageItem("pt", "Portuguese", "Português", "🇵🇹"),
        LanguageItem("pa", "Punjabi", "ਪੰਜਾਬੀ", "🇮🇳"),
        LanguageItem("qu", "Quechua", "Runasimi", "🇵🇪"),
        LanguageItem("ro", "Romanian", "Română", "🇷🇴"),
        LanguageItem("ru", "Russian", "Русский", "🇷🇺"),
        LanguageItem("sm", "Samoan", "Gagana Sāmoa", "🇼🇸"),
        LanguageItem("sa", "Sanskrit", "संस्कृतम्", "🇮🇳"),
        LanguageItem("gd", "Scots Gaelic", "Gàidhlig", "🏴󠁧󠁢󠁳󠁣󠁴󠁿"),
        LanguageItem("nso", "Sepedi", "Sepedi", "🇿🇦"),
        LanguageItem("sr", "Serbian", "Српски", "🇷🇸"),
        LanguageItem("st", "Sesotho", "Sesotho", "🇱🇸"),
        LanguageItem("sn", "Shona", "ChiShona", "🇿🇼"),
        LanguageItem("sd", "Sindhi", "سنڌي", "🇵🇰"),
        LanguageItem("si", "Sinhala", "සිංහල", "🇱🇰"),
        LanguageItem("sk", "Slovak", "Slovenčina", "🇸🇰"),
        LanguageItem("sl", "Slovenian", "Slovenščina", "🇸🇮"),
        LanguageItem("so", "Somali", "Soomaali", "🇸🇴"),
        LanguageItem("es", "Spanish", "Español", "🇪🇸"),
        LanguageItem("su", "Sundanese", "Basa Sunda", "🇮🇩"),
        LanguageItem("sw", "Swahili", "Kiswahili", "🇰🇪"),
        LanguageItem("sv", "Swedish", "Svenska", "🇸🇪"),
        LanguageItem("tg", "Tajik", "Тоҷикӣ", "🇹🇯"),
        LanguageItem("ta", "Tamil", "தமிழ்", "🇮🇳"),
        LanguageItem("tt", "Tatar", "Татарча", "🌍"),
        LanguageItem("te", "Telugu", "తెలుగు", "🇮🇳"),
        LanguageItem("th", "Thai", "ไทย", "🇹🇭"),
        LanguageItem("ti", "Tigrinya", "ትግርኛ", "🇪🇷"),
        LanguageItem("ts", "Tsonga", "Xitsonga", "🇿🇦"),
        LanguageItem("tr", "Turkish", "Türkçe", "🇹🇷"),
        LanguageItem("tk", "Turkmen", "Türkmençe", "🇹🇲"),
        LanguageItem("ak", "Twi (Akan)", "Twi", "🇬🇭"),
        LanguageItem("uk", "Ukrainian", "Українська", "🇺🇦"),
        LanguageItem("ur", "Urdu", "اردو", "🇵🇰"),
        LanguageItem("ug", "Uyghur", "ئۇيغۇرچە", "🌍"),
        LanguageItem("uz", "Uzbek", "Oʻzbek", "🇺🇿"),
        LanguageItem("vi", "Vietnamese", "Tiếng Việt", "🇻🇳"),
        LanguageItem("cy", "Welsh", "Cymraeg", "🏴󠁧󠁢󠁷󠁬󠁳󠁿"),
        LanguageItem("xh", "Xhosa", "isiXhosa", "🇿🇦"),
        LanguageItem("yi", "Yiddish", "ייִדיש", "🌍"),
        LanguageItem("yo", "Yoruba", "Yorùbá", "🇳🇬"),
        LanguageItem("zu", "Zulu", "isiZulu", "🇿🇦")
    ).sortedBy { it.name.lowercase() }

    val all: List<LanguageItem> = pinnedTop + others

    fun findByCode(code: String): LanguageItem {
        val clean = code.trim()
        return all.firstOrNull { it.code.equals(clean, ignoreCase = true) }
            ?: all.first()
    }
}
