package app.lawnchair.hotseat

import org.junit.Assert.assertEquals
import org.junit.Test

class DockCenteringTest {

    // 5 slots of 200px, no border: content 1000px, slot lefts 0, 200, 400, 600, 800.
    private fun offsets(vararg lefts: Int) = DockCentering.offsets(lefts.toList(), cols = 5, cellWidth = 200, border = 0)

    @Test
    fun full_noShift() = assertEquals(listOf(0f, 0f, 0f, 0f, 0f), offsets(0, 200, 400, 600, 800))

    // 3 icons with a gap at slot 1: group of 600px centered starts at 200.
    @Test
    fun threeWithGap_centered() = assertEquals(listOf(200f, -200f, -200f), offsets(0, 600, 800))

    @Test
    fun one_inMiddle() = assertEquals(listOf(-400f), offsets(800))

    // Input order is kept; ranking is by position, so RTL-mirrored lefts work the same.
    @Test
    fun unsortedInput_keepsOrder() = assertEquals(listOf(-100f, 300f), offsets(600, 0))

    @Test
    fun none_empty() = assertEquals(emptyList<Float>(), offsets())

    // 4 icons in 5 slots with a 20px border: stride 220, content 1080, group 860, start 110.
    @Test
    fun border_inStride() = assertEquals(
        listOf(110f, 110f, 110f, -110f),
        DockCentering.offsets(listOf(0, 220, 440, 880), cols = 5, cellWidth = 200, border = 20),
    )
}
