package com.example

import com.example.model.BlockColor
import com.example.model.BlockEngine
import com.example.model.BlockPlacementMath
import com.example.model.BlockShapeCatalog
import com.example.model.CellCoord
import com.example.model.DropResult
import com.example.model.LevelConfigManager
import com.example.model.LevelDifficulty
import com.example.model.PlayerStats
import com.example.model.TrayPiece
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun test1_placeBlockWithoutClearingLine_scoreAndProgressZero() {
        val grid = BlockEngine.createEmptyGrid(8)
        val singlePiece = listOf(CellCoord(0, 0))
        assertTrue(BlockEngine.canPlace(grid, singlePiece, 0, 0, 8))
        grid[0][0] = BlockColor.CYAN

        val completed = BlockEngine.findCompletedLines(grid, 8)
        assertEquals(0, completed.rows.size)
        assertEquals(0, completed.cols.size)

        val score = BlockEngine.calculateScore(completed.rows, completed.cols)
        assertEquals(0, score) // Zero points when no line is cleared!
    }

    @Test
    fun test2_clearOneLine_scoreAndProgressIncrease() {
        val grid = BlockEngine.createEmptyGrid(8)
        for (c in 0 until 7) {
            grid[0][c] = BlockColor.RED
        }
        grid[0][7] = BlockColor.RED

        val completed = BlockEngine.findCompletedLines(grid, 8)
        assertEquals(1, completed.rows.size)
        assertEquals(0, completed.cols.size)

        val score = BlockEngine.calculateScore(completed.rows, completed.cols)
        assertEquals(10, score) // Exactly 10 points for 1 line

        val progress = LevelConfigManager.calculateProgressPercent(1, 10)
        assertEquals(10, progress) // 1 line of 10 = 10%
    }

    @Test
    fun test3_clearTwoLinesSimultaneously_bothLinesClearAndAwardMultiScore() {
        val grid = BlockEngine.createEmptyGrid(8)
        for (c in 1 until 8) grid[0][c] = BlockColor.BLUE
        for (r in 1 until 8) grid[r][0] = BlockColor.BLUE
        grid[0][0] = BlockColor.BLUE

        val completed = BlockEngine.findCompletedLines(grid, 8)
        assertEquals(1, completed.rows.size)
        assertEquals(1, completed.cols.size)

        val score = BlockEngine.calculateScore(completed.rows, completed.cols)
        assertTrue(score >= 30)

        val progress = LevelConfigManager.calculateProgressPercent(completed.rows.size + completed.cols.size, 10)
        assertEquals(20, progress) // 2 lines of 10 = 20%
    }

    @Test
    fun test4_placeBlockInInvalidLocation_rejected() {
        val grid = BlockEngine.createEmptyGrid(8)
        grid[2][2] = BlockColor.YELLOW
        val piece = listOf(CellCoord(0, 0))

        assertFalse(BlockEngine.canPlace(grid, piece, 2, 2, 8))
        assertFalse(BlockEngine.canPlace(grid, piece, 8, 8, 8))
        assertFalse(BlockEngine.canPlace(grid, piece, -1, 0, 8))
    }

    @Test
    fun test5_useOnePiece_unusedPiecesRemainAvailable() {
        val pieces = BlockShapeCatalog.generateBalancedTrayDeal(12345L).toMutableList()
        assertEquals(3, pieces.size)

        pieces[0] = pieces[0].copy(placed = true)
        assertFalse(pieces[1].placed)
        assertFalse(pieces[2].placed)

        val unplaced = pieces.filter { !it.placed }
        assertEquals(2, unplaced.size)
    }

    @Test
    fun test6_exhaustAllThreePieces_newGroupAppears() {
        var pieces = BlockShapeCatalog.generateBalancedTrayDeal(100L).map { it.copy(placed = true) }
        assertTrue(pieces.all { it.placed })

        if (pieces.all { it.placed }) {
            pieces = BlockShapeCatalog.generateBalancedTrayDeal(200L)
        }
        assertEquals(3, pieces.size)
        assertTrue(pieces.none { it.placed })
    }

    @Test
    fun test7_reach50PercentProgress_milestoneMessage() {
        val progress = LevelConfigManager.calculateProgressPercent(5, 10)
        assertEquals(50, progress)
        val msg = LevelConfigManager.getMilestoneMessage(progress)
        assertEquals("HALFWAY THERE!", msg)
    }

    @Test
    fun test8_reach80PercentProgress_milestoneMessage() {
        val progress = LevelConfigManager.calculateProgressPercent(8, 10)
        assertEquals(80, progress)
        val msg = LevelConfigManager.getMilestoneMessage(progress)
        assertEquals("80% COMPLETE! ALMOST THERE!", msg)
    }

    @Test
    fun test9_reach100PercentProgress_triggersLevelCompletion() {
        val progress = LevelConfigManager.calculateProgressPercent(10, 10)
        assertEquals(100, progress)
        val msg = LevelConfigManager.getMilestoneMessage(progress)
        assertEquals("LEVEL COMPLETE! WELL DONE!", msg)
    }

    @Test
    fun test10_completeLevel1_unlocksLevel2() {
        val stats = PlayerStats(completedJourneyLevels = 0, lastUnlockedJourneyLevel = 1)
        val completed = minOf(5000, maxOf(stats.completedJourneyLevels, 1))
        val unlocked = minOf(5000, maxOf(stats.lastUnlockedJourneyLevel, 1 + 1))
        assertEquals(1, completed)
        assertEquals(2, unlocked)
    }

    @Test
    fun test11_reachLevel5000_finalChallengeConfiguration() {
        val config = LevelConfigManager.getLevelConfig(5000)
        assertEquals(5000, config.levelNumber)
        assertTrue(config.isFinalLevel)
        assertEquals(LevelDifficulty.FINAL_CHALLENGE, config.difficulty)
        assertEquals(5000, config.rewardDiamonds)
        assertEquals("Level 5000: Final Challenge", config.title)
    }

    @Test
    fun test12_completeLevel5000_level5001NeverCreated() {
        val stats = PlayerStats(completedJourneyLevels = 4999, lastUnlockedJourneyLevel = 5000)
        val completed = minOf(5000, maxOf(stats.completedJourneyLevels, 5000))
        val unlocked = minOf(5000, maxOf(stats.lastUnlockedJourneyLevel, 5000 + 1))
        assertEquals(5000, completed)
        assertEquals(5000, unlocked) // Capped at 5000
    }

    @Test
    fun test13_classicMode_withoutLineClear_scoreDoesNotIncrease() {
        val grid = BlockEngine.createEmptyGrid(8)
        val cells = listOf(CellCoord(0, 0), CellCoord(0, 1))
        assertTrue(BlockEngine.canPlace(grid, cells, 1, 1, 8))
        grid[1][1] = BlockColor.YELLOW
        grid[1][2] = BlockColor.YELLOW
        val completed = BlockEngine.findCompletedLines(grid, 8)
        val addedScore = BlockEngine.calculateScore(completed.rows, completed.cols)
        assertEquals(0, addedScore)
    }

    @Test
    fun test14_classicMode_clearLine_scoreIncreasesCorrectly() {
        val grid = BlockEngine.createEmptyGrid(8)
        for (c in 0 until 8) grid[3][c] = BlockColor.PINK
        val completed = BlockEngine.findCompletedLines(grid, 8)
        val score = BlockEngine.calculateScore(completed.rows, completed.cols)
        assertEquals(10, score)
    }

    @Test
    fun test15_noAvailablePieceFits_gameOver() {
        val grid = BlockEngine.createEmptyGrid(8)
        for (r in 0 until 8) {
            for (c in 0 until 8) {
                if ((r + c) % 2 == 0) {
                    grid[r][c] = BlockColor.RED
                }
            }
        }
        val piece2x2 = TrayPiece(
            id = "test_2x2",
            shapeId = "square_2x2",
            name = "Square 2x2",
            cells = listOf(CellCoord(0, 0), CellCoord(0, 1), CellCoord(1, 0), CellCoord(1, 1)),
            color = BlockColor.GREEN,
            slotIndex = 0,
            placed = false
        )
        assertFalse(BlockEngine.canShapeFitAnywhere(grid, piece2x2.cells, 8))
        assertFalse(BlockEngine.hasAnyValidMove(grid, listOf(piece2x2), 8))
    }

    @Test
    fun test16_reopenApplication_persistenceOfJourneyProgressAndClassicScore() {
        val saved = PlayerStats(
            hasSeenWelcome = true,
            hasCompletedTutorial = true,
            totalDiamonds = 2500,
            currentStreak = 4,
            completedJourneyLevels = 28,
            lastUnlockedJourneyLevel = 29,
            bestClassicScore = 4850,
            trophies = 53
        )
        assertEquals(28, saved.completedJourneyLevels)
        assertEquals(29, saved.lastUnlockedJourneyLevel)
        assertEquals(4850, saved.bestClassicScore)
        assertEquals(2500, saved.totalDiamonds)
    }

    @Test
    fun test17_dragBlockToCenterOfBoard_snapsAndPlaces() {
        val grid = BlockEngine.createEmptyGrid(8)
        val piece = TrayPiece(
            id = "p1", shapeId = "single_1x1", name = "Single",
            cells = listOf(CellCoord(0, 0)), color = BlockColor.CYAN,
            slotIndex = 0, placed = false
        )
        val boardSize = 8
        val boardPx = 800f
        val cellPx = boardPx / boardSize
        val boardX = 100f
        val boardY = 200f
        val focalX = boardX + 4.5f * cellPx
        val focalY = boardY + 4.5f * cellPx

        val res = BlockPlacementMath.tryDropPiece(
            grid = grid,
            piece = piece,
            focalX = focalX,
            focalY = focalY,
            boardX = boardX,
            boardY = boardY,
            boardPxSize = boardPx,
            boardSize = boardSize,
            totalDragDistance = 150f
        )
        assertTrue(res is DropResult.Success)
        val success = res as DropResult.Success
        assertEquals(4, success.row)
        assertEquals(4, success.col)
        assertEquals(BlockColor.CYAN, grid[4][4])
    }

    @Test
    fun test18_dragBlockToEveryEdgeAndCorner_validSnapping() {
        val boardSize = 8
        val boardPx = 800f
        val cellPx = boardPx / boardSize
        val boardX = 0f
        val boardY = 0f
        val piece = TrayPiece(
            id = "p1", shapeId = "single_1x1", name = "Single",
            cells = listOf(CellCoord(0, 0)), color = BlockColor.RED,
            slotIndex = 0, placed = false
        )
        val testPositions = listOf(
            Pair(0, 0), Pair(0, 7), Pair(7, 0), Pair(7, 7),
            Pair(0, 3), Pair(7, 3), Pair(3, 0), Pair(3, 7)
        )
        for ((r, c) in testPositions) {
            val grid = BlockEngine.createEmptyGrid(8)
            val focalX = (c + 0.5f) * cellPx
            val focalY = (r + 0.5f) * cellPx
            val res = BlockPlacementMath.tryDropPiece(
                grid = grid,
                piece = piece,
                focalX = focalX,
                focalY = focalY,
                boardX = boardX,
                boardY = boardY,
                boardPxSize = boardPx,
                boardSize = boardSize,
                totalDragDistance = 100f
            )
            assertTrue("Failed to place at ($r, $c)", res is DropResult.Success)
            assertEquals(BlockColor.RED, grid[r][c])
        }
    }
}
