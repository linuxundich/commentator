# Retrofit, OkHttp und kotlinx.serialization liefern eigene Consumer-Regeln mit.
# Hier stehen nur Regeln, die darüber hinaus nötig sind.

# Datenklassen, die ausschließlich über kotlinx.serialization instanziiert
# werden, dürfen nicht entfernt werden.
-keepclassmembers class de.christophlangner.commentator.data.remote.dto.** {
    *** Companion;
}
-keepclasseswithmembers class de.christophlangner.commentator.data.remote.dto.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Typsichere Navigationsrouten werden ebenfalls serialisiert.
-keepclasseswithmembers class de.christophlangner.commentator.ui.navigation.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# R8 im Full Mode: Nullprüfungen dürfen die Meldung verlieren, die Prüfung
# selbst bleibt erhalten.
-processkotlinnullchecks remove_message
