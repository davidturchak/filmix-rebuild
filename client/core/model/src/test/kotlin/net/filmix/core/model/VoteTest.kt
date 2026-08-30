package net.filmix.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoteTest {

    @Test
    fun `a first vote adds one to its own side only`() {
        val after = VoteTally(46, 8).optimistic(Vote.Up)

        assertEquals(47, after.positive)
        assertEquals(8, after.negative)
        assertEquals(Vote.Up, after.own)
    }

    @Test
    fun `voting the same way again changes nothing`() {
        val before = VoteTally(47, 8, own = Vote.Up)

        assertEquals(before, before.optimistic(Vote.Up))
    }

    @Test
    fun `switching sides moves the vote across`() {
        val after = VoteTally(47, 8, own = Vote.Up).optimistic(Vote.Down)

        assertEquals(46, after.positive)
        assertEquals(9, after.negative)
        assertEquals(Vote.Down, after.own)
    }

    /**
     * The counts are a prediction, and the stored vote can disagree with a
     * tally the server has since recomputed. Going negative would render as
     * "-1 likes", so the guess floors at zero and waits for the real numbers.
     */
    @Test
    fun `a switched vote never drives a count below zero`() {
        val after = VoteTally(0, 0, own = Vote.Up).optimistic(Vote.Down)

        assertEquals(0, after.positive)
        assertEquals(1, after.negative)
    }

    @Test
    fun `percentage is the share of up votes, rounded`() {
        assertEquals(85, VoteTally(46, 8).percentPositive)
        assertEquals(100, VoteTally(3, 0).percentPositive)
        assertEquals(0, VoteTally(0, 3).percentPositive)
    }

    /** The original app prints "-" rather than dividing by zero. */
    @Test
    fun `percentage is unknown when nobody has voted`() {
        assertNull(VoteTally(0, 0).percentPositive)
    }

    @Test
    fun `net is what the poster badge shows`() {
        assertEquals(38, VoteTally(46, 8).net)
        assertEquals(-3, VoteTally(1, 4).net)
    }

    @Test
    fun `api values are the characters the endpoint expects`() {
        assertEquals("+", Vote.Up.apiValue)
        assertEquals("-", Vote.Down.apiValue)
        assertEquals(Vote.Up, Vote.fromApiValue("+"))
        assertEquals(Vote.Down, Vote.fromApiValue("-"))
        assertNull(Vote.fromApiValue(null))
        assertNull(Vote.fromApiValue(""))
    }
}
