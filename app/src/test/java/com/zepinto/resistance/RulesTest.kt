package com.zepinto.resistance

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun names(n: Int) = (1..n).map { "P$it" }

/** A game with a known leader and known roles: the first spyCount players are spies. */
private fun game(n: Int = 5, leader: Int = 0): Game {
    val g = Rules.newGame(names(n), Random(1))
    val spies = Rules.spyCount(n)
    return g.copy(roles = List(n) { if (it < spies) Role.SPY else Role.RESISTANCE }, leader = leader)
}

/** The first players as a team of the size the current mission needs. */
private fun team(g: Game) = (0 until g.teamSize).toList()

class SetupRulesTest {
    @Test
    fun theNumberOfSpiesFollowsTheOfficialTable() {
        assertEquals(listOf(2, 2, 3, 3, 3, 4), (5..10).map { Rules.spyCount(it) })
    }

    @Test
    fun teamSizesFollowTheOfficialTable() {
        assertEquals(listOf(2, 3, 2, 3, 3), (0..4).map { Rules.teamSize(5, it) })
        assertEquals(listOf(2, 3, 4, 3, 4), (0..4).map { Rules.teamSize(6, it) })
        assertEquals(listOf(2, 3, 3, 4, 4), (0..4).map { Rules.teamSize(7, it) })
        for (n in 8..10) assertEquals(listOf(3, 4, 4, 5, 5), (0..4).map { Rules.teamSize(n, it) })
    }

    @Test
    fun theFourthMissionNeedsTwoFailsFromSevenPlayers() {
        for (n in 5..10) for (m in 0..4) {
            val expected = if (n >= 7 && m == 3) 2 else 1
            assertEquals("n=$n mission=$m", expected, Rules.failsNeeded(n, m))
        }
    }

    @Test
    fun aNewGameDealsTheRightSpiesAndStartsAtMissionOne() {
        for (n in 5..10) repeat(20) { seed ->
            val g = Rules.newGame(names(n), Random(seed))
            assertEquals(Rules.spyCount(n), g.roles.count { it == Role.SPY })
            assertEquals(n - Rules.spyCount(n), g.roles.count { it == Role.RESISTANCE })
            assertTrue(g.leader in 0 until n)
            assertEquals(0, g.mission)
            assertEquals(0, g.rejects)
            assertTrue(g.results.all { it == null })
            assertFalse(g.over)
        }
    }

    @Test
    fun theGameIsOnlyForFiveToTenPlayers() {
        for (n in listOf(0, 3, 4, 11)) {
            var refused = false
            try {
                Rules.newGame(names(n))
            } catch (_: IllegalArgumentException) {
                refused = true
            }
            assertTrue("n=$n", refused)
        }
    }

    @Test
    fun rolesAreShuffledAcrossGames() {
        val roleOfFirst = (0 until 40).map { Rules.newGame(names(5), Random(it)).roles[0] }.toSet()
        assertEquals(setOf(Role.SPY, Role.RESISTANCE), roleOfFirst)
    }
}

class VoteRulesTest {
    @Test
    fun aTeamNeedsAStrictMajority() {
        assertTrue(Rules.isApproved(5, setOf(0, 1, 2)))
        assertFalse(Rules.isApproved(5, setOf(0, 1)))
        assertFalse("a tie rejects", Rules.isApproved(6, setOf(0, 1, 2)))
        assertTrue(Rules.isApproved(6, setOf(0, 1, 2, 3)))
        assertFalse(Rules.isApproved(5, emptySet()))
    }

    @Test
    fun anApprovedTeamWaitsForItsMission() {
        val g = game()
        val after = Rules.vote(g, team(g), setOf(0, 1, 2, 3))
        assertEquals(team(g), after.pendingTeam)
        assertEquals(0, after.rejects)
        assertEquals(g.leader, after.leader) // the leader does not change until the mission is played
        assertEquals(1, after.history.size)
        assertTrue(after.history.single().approved)
    }

    @Test
    fun aRejectedTeamPassesTheLeadershipAndCountsAsARejection() {
        val g = game(leader = 4)
        val after = Rules.vote(g, team(g), setOf(0))
        assertNull(after.pendingTeam)
        assertEquals(1, after.rejects)
        assertEquals(0, after.leader) // wraps round the table
        assertEquals(0, after.mission)
        assertFalse(after.history.single().approved)
        assertEquals(setOf(0), after.history.single().approvers)
    }

    @Test
    fun fiveRejectionsInARowWinTheGameForTheSpies() {
        var g = game()
        repeat(4) {
            g = Rules.vote(g, team(g), emptySet())
            assertFalse(g.over)
        }
        assertEquals(4, g.rejects)
        g = Rules.vote(g, team(g), emptySet())
        assertEquals(Side.SPIES, g.winner)
        assertEquals(WinReason.REJECTIONS, g.winReason)
    }

    @Test
    fun badTeamsAreIgnored() {
        val g = game()
        assertEquals(g, Rules.vote(g, listOf(0), setOf(0, 1, 2)))               // wrong size (needs 2)
        assertEquals(g, Rules.vote(g, listOf(0, 0), setOf(0, 1, 2)))            // the same player twice
        assertEquals(g, Rules.vote(g, listOf(0, 9), setOf(0, 1, 2)))            // not a player
        assertEquals(g, Rules.vote(g, listOf(0, 1), setOf(0, 1, 7)))            // a voter that does not exist
        val pending = Rules.vote(g, team(g), setOf(0, 1, 2))
        assertEquals(pending, Rules.vote(pending, team(g), setOf(0, 1, 2)))     // a mission is already waiting
    }
}

class MissionRulesTest {
    private fun played(g: Game, fails: Int) = Rules.finishMission(Rules.vote(g, team(g), setOf(0, 1, 2, 3)), fails)

    @Test
    fun onlyASpyCanPlayAFailCard() {
        assertTrue(Rules.playsFail(Role.SPY, true))
        assertFalse(Rules.playsFail(Role.SPY, false))
        assertFalse(Rules.playsFail(Role.RESISTANCE, true))
        assertFalse(Rules.playsFail(Role.RESISTANCE, false))
    }

    @Test
    fun aMissionWithNoFailCardSucceeds() {
        val after = played(game(), 0)
        assertEquals(MissionResult.SUCCESS, after.results[0])
        assertEquals(1, after.mission)
        assertNull(after.pendingTeam)
        assertEquals(0, after.history.last().fails)
        assertEquals(MissionResult.SUCCESS, after.history.last().result)
    }

    @Test
    fun oneFailCardFailsAMissionThatNeedsOne() {
        assertEquals(MissionResult.FAIL, played(game(), 1).results[0])
    }

    @Test
    fun theFourthMissionOfALargeGameNeedsTwoFails() {
        var g = game(n = 7)
        g = g.copy(mission = 3, results = listOf(MissionResult.SUCCESS, MissionResult.FAIL, MissionResult.SUCCESS, null, null))
        val one = Rules.finishMission(Rules.vote(g, team(g), setOf(0, 1, 2, 3)), 1)
        assertEquals("one fail is not enough", MissionResult.SUCCESS, one.results[3])
        val two = Rules.finishMission(Rules.vote(g, team(g), setOf(0, 1, 2, 3)), 2)
        assertEquals(MissionResult.FAIL, two.results[3])
        // but in a 5 player game the same mission fails with a single card
        var small = game(n = 5)
        small = small.copy(mission = 3)
        assertEquals(MissionResult.FAIL, Rules.finishMission(Rules.vote(small, team(small), setOf(0, 1, 2)), 1).results[3])
    }

    @Test
    fun afterAMissionTheLeadershipPassesAndRejectionsStartAgain() {
        var g = game(leader = 2)
        g = Rules.vote(g, team(g), emptySet())             // one rejection, leader 3
        assertEquals(1, g.rejects)
        assertEquals(3, g.leader)
        val after = played(g, 0)
        assertEquals(0, after.rejects)
        assertEquals(4, after.leader)
    }

    @Test
    fun threeSuccessfulMissionsWinForTheResistance() {
        var g = game()
        repeat(3) {
            assertFalse(g.over)
            g = played(g, 0)
        }
        assertEquals(Side.RESISTANCE, g.winner)
        assertEquals(WinReason.MISSIONS, g.winReason)
        assertEquals(3, g.successes)
    }

    @Test
    fun threeFailedMissionsWinForTheSpies() {
        var g = game()
        repeat(3) { g = played(g, 1) }
        assertEquals(Side.SPIES, g.winner)
        assertEquals(WinReason.MISSIONS, g.winReason)
    }

    @Test
    fun aMixOfResultsKeepsPlayingUntilThreeOfAKind() {
        var g = game()
        g = played(g, 0); g = played(g, 1); g = played(g, 0); g = played(g, 1)
        assertFalse("2-2 so far", g.over)
        assertEquals(4, g.mission)
        g = played(g, 1)
        assertEquals(Side.SPIES, g.winner)
        assertEquals(3, g.failures)
    }

    @Test
    fun nothingHappensOutOfOrderOrAfterTheEnd() {
        val g = game()
        assertEquals(g, Rules.finishMission(g, 0))                                   // no approved team
        val pending = Rules.vote(g, team(g), setOf(0, 1, 2))
        assertEquals(pending, Rules.finishMission(pending, 9))                        // more fails than players on the team
        assertEquals(pending, Rules.finishMission(pending, -1))
        var over = game()
        repeat(3) { over = played(over, 0) }
        assertEquals(over, Rules.vote(over, team(over), setOf(0, 1, 2)))
    }
}

class SaveRulesTest {
    @Test
    fun aGameInProgressComesBackExactly() {
        var g = game(n = 7, leader = 3)
        g = Rules.vote(g, team(g), setOf(0))
        g = Rules.vote(g, team(g), setOf(0, 1, 2, 3))
        g = Rules.finishMission(g, 1)
        assertEquals(g, GameCodec.load(GameCodec.save(g)))
        val mid = Rules.vote(game(), team(game()), setOf(0, 1, 2)) // a mission waiting
        assertEquals(mid, GameCodec.load(GameCodec.save(mid)))
        var done = game()
        repeat(3) { done = Rules.finishMission(Rules.vote(done, team(done), setOf(0, 1, 2)), 0) }
        assertEquals(done, GameCodec.load(GameCodec.save(done)))
    }

    @Test
    fun brokenSavesAreIgnored() {
        for (bad in listOf("", "{}", "nope", "{\"players\":[\"a\"]}")) assertNull(bad, GameCodec.load(bad))
    }
}
