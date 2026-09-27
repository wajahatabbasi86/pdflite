package com.trendoc.pdflite.billing

import org.junit.Assert.assertEquals
import org.junit.Test

class EntitlementWindowTest {

    private val now = 1_000_000L
    private val hour = 60 * 60 * 1000L

    @Test fun `no window yet starts from now`() {
        assertEquals(now + 2 * hour, extendAdFreeUntil(null, now, 2 * hour))
    }

    @Test fun `an expired window starts from now, not from the old expiry`() {
        assertEquals(now + 2 * hour, extendAdFreeUntil(now - 5 * hour, now, 2 * hour))
    }

    @Test fun `a running window is extended, not overwritten`() {
        assertEquals(now + 3 * hour, extendAdFreeUntil(now + hour, now, 2 * hour))
    }

    @Test fun `a purchase on top of a rewarded video stacks both`() {
        val afterVideo = extendAdFreeUntil(null, now, VIDEO_REWARD_DURATION_MILLIS)
        val afterPurchase = extendAdFreeUntil(afterVideo, now, PAID_REMOVAL_DURATION_MILLIS)
        assertEquals(now + VIDEO_REWARD_DURATION_MILLIS + PAID_REMOVAL_DURATION_MILLIS, afterPurchase)
    }

    @Test fun `the durations match what the listing and privacy policy promise`() {
        assertEquals(2 * hour, VIDEO_REWARD_DURATION_MILLIS)
        assertEquals(24 * hour, PAID_REMOVAL_DURATION_MILLIS)
    }
}
