package com.pixelbead

import com.pixelbead.workbench.WorkbenchLogic
import com.pixelbead.workbench.WorkbenchLogic.Status
import kotlin.test.Test
import kotlin.test.assertEquals

class WorkbenchLogicTest {
    @Test
    fun emptyWhenNoInputAndNoDye() =
        assertEquals(Status.EMPTY, WorkbenchLogic.evaluate(0, false, null, null, false, 16))

    @Test
    fun badDyeWhenMissingOrUnmapped() =
        assertEquals(Status.BAD_DYE, WorkbenchLogic.evaluate(16, true, null, "a1", true, 16))

    @Test
    fun notConvertibleInput() =
        assertEquals(Status.NOT_CONVERTIBLE, WorkbenchLogic.evaluate(16, false, "A", "a1", true, 16))

    @Test
    fun needMoreInputBelowBatch() =
        assertEquals(Status.NEED_MORE_INPUT, WorkbenchLogic.evaluate(15, true, "A", "a1", true, 16))

    @Test
    fun noSelection() =
        assertEquals(Status.NO_SELECTION, WorkbenchLogic.evaluate(16, true, "A", null, false, 16))

    @Test
    fun selectionOutsideDyeSeries() =
        assertEquals(Status.NO_SELECTION, WorkbenchLogic.evaluate(16, true, "A", "b1", false, 16))

    @Test
    fun okWhenAllValid() =
        assertEquals(Status.OK, WorkbenchLogic.evaluate(16, true, "A", "a1", true, 16))

    @Test
    fun customBatchSize() =
        assertEquals(Status.OK, WorkbenchLogic.evaluate(8, true, "A", "a1", true, 8))
}