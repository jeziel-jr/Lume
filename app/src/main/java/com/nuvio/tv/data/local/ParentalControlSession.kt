package com.nuvio.tv.data.local

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process-wide flag: the adult categories were unlocked with the PIN during
 * this app session. Resets on process death.
 */
@Singleton
class ParentalControlSession @Inject constructor() {
    @Volatile
    var adultUnlocked: Boolean = false

    fun unlock() {
        adultUnlocked = true
    }
}
