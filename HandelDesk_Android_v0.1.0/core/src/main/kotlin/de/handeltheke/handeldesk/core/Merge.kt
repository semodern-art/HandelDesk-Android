package de.handeltheke.handeldesk.core

enum class MergeStatus { SAFE, ALREADY_APPLIED, CONFLICT }

data class MergeCheck<T>(val status: MergeStatus, val base: T?, val local: T?, val server: T?)

object ThreeWayMerge {
    fun <T> compare(base: T?, local: T?, server: T?): MergeCheck<T> = when {
        server == base -> MergeCheck(MergeStatus.SAFE, base, local, server)
        local == server -> MergeCheck(MergeStatus.ALREADY_APPLIED, base, local, server)
        else -> MergeCheck(MergeStatus.CONFLICT, base, local, server)
    }
}
