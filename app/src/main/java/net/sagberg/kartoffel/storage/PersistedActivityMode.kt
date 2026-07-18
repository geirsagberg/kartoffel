package net.sagberg.kartoffel.storage

internal enum class PersistedActivityMode(val persistedName: String) {
    STILL("still"),
    WALKING("walking"),
    RUNNING("running"),
    CYCLING("cycling"),
    IN_VEHICLE("in_vehicle"),
    UNKNOWN("unknown"),
    ;

    companion object {
        fun fromPersistedName(value: String): PersistedActivityMode =
            entries.firstOrNull { it.persistedName == value } ?: UNKNOWN
    }
}
