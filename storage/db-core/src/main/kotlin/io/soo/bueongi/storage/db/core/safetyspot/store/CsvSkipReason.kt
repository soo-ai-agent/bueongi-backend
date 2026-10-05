package io.soo.bueongi.storage.db.core.safetyspot.store

enum class CsvSkipReason {
    TOO_FEW_COLUMNS,
    NOT_SECURITY_PURPOSE,
    NO_COORDINATE,
    NO_COORDINATE_COLUMNS,
}
