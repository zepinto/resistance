package com.zepinto.resistance

import kotlin.random.Random
import org.json.JSONArray
import org.json.JSONObject

enum class Role { RESISTANCE, SPY }
enum class Side { RESISTANCE, SPIES }
enum class MissionResult { SUCCESS, FAIL }
enum class WinReason { MISSIONS, REJECTIONS }

/** One proposed team, the vote on it and, if it was approved, what happened on the mission. */
data class Round(
    val mission: Int,
    val leader: Int,
    val team: List<Int>,
    /** Players who voted to approve the team. */
    val approvers: Set<Int>,
    val approved: Boolean,
    /** Fail cards played, known once the mission has been played. */
    val fails: Int? = null,
    val result: MissionResult? = null,
)

data class Game(
    val players: List<String>,
    val roles: List<Role>,
    /** Index of the player who proposes the next team. The role passes clockwise (down the list). */
    val leader: Int,
    /** The mission being played, 0 to 4. */
    val mission: Int,
    val results: List<MissionResult?>,
    /** Teams rejected in a row since the last mission was played. */
    val rejects: Int,
    /** An approved team waiting to play its mission. */
    val pendingTeam: List<Int>? = null,
    val winner: Side? = null,
    val winReason: WinReason? = null,
    val history: List<Round> = emptyList(),
) {
    val n get() = players.size
    val over get() = winner != null
    val successes get() = results.count { it == MissionResult.SUCCESS }
    val failures get() = results.count { it == MissionResult.FAIL }
    val teamSize get() = Rules.teamSize(n, mission)
    val failsNeeded get() = Rules.failsNeeded(n, mission)
    val spies get() = roles.indices.filter { roles[it] == Role.SPY }
}

object Rules {
    const val MIN_PLAYERS = 5
    const val MAX_PLAYERS = 10
    const val MISSIONS = 5
    const val WINS_NEEDED = 3
    const val MAX_REJECTS = 5

    private val SPIES = mapOf(5 to 2, 6 to 2, 7 to 3, 8 to 3, 9 to 3, 10 to 4)

    /** Players on the team of each of the 5 missions, by number of players. */
    private val TEAMS = mapOf(
        5 to listOf(2, 3, 2, 3, 3),
        6 to listOf(2, 3, 4, 3, 4),
        7 to listOf(2, 3, 3, 4, 4),
        8 to listOf(3, 4, 4, 5, 5),
        9 to listOf(3, 4, 4, 5, 5),
        10 to listOf(3, 4, 4, 5, 5),
    )

    fun spyCount(players: Int) = SPIES.getValue(players)
    fun teamSize(players: Int, mission: Int) = TEAMS.getValue(players)[mission]

    /** Fail cards a mission needs to fail: one, except the fourth mission with 7 or more players, which needs two. */
    fun failsNeeded(players: Int, mission: Int) = if (players >= 7 && mission == 3) 2 else 1

    fun newGame(players: List<String>, rng: Random = Random.Default): Game {
        require(players.size in MIN_PLAYERS..MAX_PLAYERS) { "the game is for $MIN_PLAYERS to $MAX_PLAYERS players" }
        val roles = (List(spyCount(players.size)) { Role.SPY } + List(players.size - spyCount(players.size)) { Role.RESISTANCE }).shuffled(rng)
        return Game(
            players = players,
            roles = roles,
            leader = rng.nextInt(players.size),
            mission = 0,
            results = List(MISSIONS) { null },
            rejects = 0,
        )
    }

    /** A team is approved only by a strict majority: a tie rejects it. */
    fun isApproved(players: Int, approvers: Set<Int>) = approvers.size * 2 > players

    /**
     * The leader's team has been voted on. Approved: the team waits to play its mission.
     * Rejected: the leadership passes on, and the fifth rejection in a row wins the game for the spies.
     */
    fun vote(g: Game, team: List<Int>, approvers: Set<Int>): Game {
        if (g.over || g.pendingTeam != null) return g
        if (team.size != g.teamSize || team.toSet().size != team.size || team.any { it !in 0 until g.n }) return g
        if (approvers.any { it !in 0 until g.n }) return g
        val approved = isApproved(g.n, approvers)
        val round = Round(g.mission, g.leader, team, approvers, approved)
        if (approved) {
            return g.copy(pendingTeam = team, history = g.history + round)
        }
        val rejects = g.rejects + 1
        val next = g.copy(leader = (g.leader + 1) % g.n, rejects = rejects, history = g.history + round)
        return if (rejects >= MAX_REJECTS) next.copy(winner = Side.SPIES, winReason = WinReason.REJECTIONS) else next
    }

    /** What a player puts on the mission: only a spy can choose to fail it. True means a fail card. */
    fun playsFail(role: Role, wantsFail: Boolean) = role == Role.SPY && wantsFail

    /**
     * The approved team has played, with [fails] fail cards among their cards. The mission is decided,
     * the leadership passes on and the count of rejections starts again. Three missions of one kind win the game.
     */
    fun finishMission(g: Game, fails: Int): Game {
        val team = g.pendingTeam ?: return g
        if (g.over || fails !in 0..team.size) return g
        val result = if (fails >= g.failsNeeded) MissionResult.FAIL else MissionResult.SUCCESS
        val results = g.results.toMutableList().also { it[g.mission] = result }
        val history = g.history.toMutableList().also { it[it.lastIndex] = it.last().copy(fails = fails, result = result) }
        val next = g.copy(
            results = results,
            history = history,
            pendingTeam = null,
            rejects = 0,
            leader = (g.leader + 1) % g.n,
            mission = (g.mission + 1).coerceAtMost(MISSIONS - 1),
        )
        return when {
            next.successes >= WINS_NEEDED -> next.copy(winner = Side.RESISTANCE, winReason = WinReason.MISSIONS)
            next.failures >= WINS_NEEDED -> next.copy(winner = Side.SPIES, winReason = WinReason.MISSIONS)
            else -> next
        }
    }
}

/** Saving a game in progress, so that closing the app does not lose it. */
object GameCodec {
    private fun ints(l: Collection<Int>) = JSONArray(l.toList())
    private fun parseInts(a: JSONArray) = List(a.length()) { a.getInt(it) }

    fun save(g: Game): String = JSONObject()
        .put("players", JSONArray(g.players))
        .put("roles", JSONArray(g.roles.map { it.ordinal }))
        .put("leader", g.leader).put("mission", g.mission)
        .put("results", JSONArray(g.results.map { it?.ordinal ?: -1 }))
        .put("rejects", g.rejects)
        .put("pending", g.pendingTeam?.let { ints(it) } ?: JSONObject.NULL)
        .put("winner", g.winner?.ordinal ?: -1).put("why", g.winReason?.ordinal ?: -1)
        .put(
            "history",
            JSONArray(
                g.history.map {
                    JSONObject().put("m", it.mission).put("l", it.leader).put("team", ints(it.team)).put("yes", ints(it.approvers.sorted()))
                        .put("ok", it.approved).put("fails", it.fails ?: -1).put("res", it.result?.ordinal ?: -1)
                }
            )
        )
        .toString()

    fun load(text: String): Game? = try {
        val o = JSONObject(text)
        val players = o.getJSONArray("players").let { a -> List(a.length()) { a.getString(it) } }
        val roles = parseInts(o.getJSONArray("roles")).map { Role.entries[it] }
        val results = parseInts(o.getJSONArray("results")).map { n -> n.takeIf { it >= 0 }?.let { MissionResult.entries[it] } }
        require(players.size in Rules.MIN_PLAYERS..Rules.MAX_PLAYERS && roles.size == players.size && results.size == Rules.MISSIONS)
        val history = o.getJSONArray("history").let { a ->
            List(a.length()) {
                val r = a.getJSONObject(it)
                Round(
                    mission = r.getInt("m"), leader = r.getInt("l"), team = parseInts(r.getJSONArray("team")),
                    approvers = parseInts(r.getJSONArray("yes")).toSet(), approved = r.getBoolean("ok"),
                    fails = r.getInt("fails").takeIf { f -> f >= 0 },
                    result = r.getInt("res").takeIf { s -> s >= 0 }?.let { s -> MissionResult.entries[s] },
                )
            }
        }
        val seats = players.indices
        history.forEach { r ->
            require(r.leader in seats && r.team.all { it in seats } && r.approvers.all { it in seats } && r.mission in 0 until Rules.MISSIONS)
        }
        Game(
            players = players,
            roles = roles,
            leader = o.getInt("leader").also { require(it in players.indices) },
            mission = o.getInt("mission").also { require(it in 0 until Rules.MISSIONS) },
            results = results,
            rejects = o.getInt("rejects").also { require(it in 0 until Rules.MAX_REJECTS) },
            pendingTeam = if (o.isNull("pending")) null else parseInts(o.getJSONArray("pending")).also { p -> require(p.all { it in seats }) },
            winner = o.getInt("winner").takeIf { it >= 0 }?.let { Side.entries[it] },
            winReason = o.getInt("why").takeIf { it >= 0 }?.let { WinReason.entries[it] },
            history = history,
        )
    } catch (_: Exception) {
        null
    }
}
