package app.foldcade.language

import org.junit.Assert.assertEquals
import org.junit.Test

class DisplaySourceTest {
    @Test fun identityAndRearrangedLookupsMatchFullOrderIncludingOutOfRangeIndices() {
        for (count in listOf(0, 1, 49, 2000)) {
            for (order in listOf(emptyList(), List(count) { count - it - 1 }, listOf(8, 7))) {
                for (arranging in listOf(false, true)) {
                    val model = PickerModel(count = count, rowsPerPage = 2, order = order, arranging = arranging)
                    val full = displayOrder(model)
                    for (index in -1..count) assertEquals(full.getOrElse(index) { index }, displaySource(model, index))
                }
            }
        }
    }
}
