package com.pixelbead.workbench

/**
 * Pure, registry-free workbench validation so it can be unit-tested without Minecraft.
 * The menu computes the primitive inputs (counts, flags, series) and calls evaluate().
 */
object WorkbenchLogic {
    enum class Status { EMPTY, NEED_MORE_INPUT, BAD_DYE, NOT_CONVERTIBLE, NO_SELECTION, OK }

    /**
     * @param inputCount        number of items in the input slot
     * @param inputConvertible  whether the input item passes ConvertibleBlocks
     * @param dyeSeries         MARD series mapped from the dye, or null if dye missing/unmapped
     * @param selectedColorId   currently selected bead color id, or null
     * @param selectedInSeries  whether the selected color belongs to dyeSeries
     * @param batchIn           required input count (config blocks_per_dye)
     */
    fun evaluate(
        inputCount: Int,
        inputConvertible: Boolean,
        dyeSeries: String?,
        selectedColorId: String?,
        selectedInSeries: Boolean,
        batchIn: Int,
    ): Status {
        if (inputCount <= 0 && dyeSeries == null) return Status.EMPTY
        if (dyeSeries == null) return Status.BAD_DYE
        if (inputCount > 0 && !inputConvertible) return Status.NOT_CONVERTIBLE
        if (inputCount < batchIn) return Status.NEED_MORE_INPUT
        if (selectedColorId == null || !selectedInSeries) return Status.NO_SELECTION
        return Status.OK
    }
}