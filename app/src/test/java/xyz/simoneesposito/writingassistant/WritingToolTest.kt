package xyz.simoneesposito.writingassistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.simoneesposito.writingassistant.tools.WritingTool

class WritingToolTest {

    @Test
    fun allToolsHaveUniversalConstraint() {
        val marker = "CRITICAL RULES"
        WritingTool.entries.forEach { tool ->
            assertTrue(
                "${tool.name} systemPrompt is missing the universal constraint",
                tool.systemPrompt.contains(marker)
            )
        }
    }

    @Test
    fun allToolsHaveNonEmptyLabel() {
        WritingTool.entries.forEach { tool ->
            assertTrue("${tool.name} has empty label", tool.label.isNotBlank())
        }
    }

    @Test
    fun allToolsHaveNonEmptyDescription() {
        WritingTool.entries.forEach { tool ->
            assertTrue("${tool.name} has empty description", tool.description.isNotBlank())
        }
    }

    @Test
    fun allNineToolsPresent() {
        assertEquals("Expected exactly 9 tools", 9, WritingTool.entries.size)
    }

    @Test
    fun allTemperaturesAreInValidRange() {
        WritingTool.entries.forEach { tool ->
            assertTrue(
                "${tool.name} temperature ${tool.temperature} must be in 0.0..1.0",
                tool.temperature in 0f..1f
            )
        }
    }

    @Test
    fun proofreadHasLowestTemperature() {
        val proofreadTemp = WritingTool.PROOFREAD.temperature
        WritingTool.entries
            .filter { it != WritingTool.PROOFREAD }
            .forEach { tool ->
                assertTrue(
                    "${tool.name} (${tool.temperature}) should have temperature >= PROOFREAD ($proofreadTemp)",
                    tool.temperature >= proofreadTemp
                )
            }
    }

    @Test
    fun noTwoToolsShareTheSameLabel() {
        val labels = WritingTool.entries.map { it.label }
        assertEquals("Tool labels must be unique", labels.size, labels.toSet().size)
    }

    @Test
    fun structureToolsHaveLowerTemperatureThanCreativeTools() {
        assertTrue(WritingTool.TABLE.temperature < WritingTool.REWRITE.temperature)
        assertTrue(WritingTool.LIST.temperature < WritingTool.REWRITE.temperature)
    }
}
