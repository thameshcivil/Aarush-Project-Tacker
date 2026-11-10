package com.aarush.cpm.data.repository

import androidx.room.withTransaction
import com.aarush.cpm.data.database.AppDatabase
import com.aarush.cpm.data.entity.CashEntry
import com.aarush.cpm.data.entity.CashEntryType
import com.aarush.cpm.data.entity.ProjectPartner
import com.aarush.cpm.domain.calculation.CashLedger
import com.aarush.cpm.domain.calculation.CashPosition
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** Office cash, partners' profit and refills (v12). Calculations live in CashLedger. */
class CashRepository(private val db: AppDatabase) {

    fun observePartners(projectId: Long): Flow<List<ProjectPartner>> = db.cashDao().observePartners(projectId)
    fun observeEntries(projectId: Long): Flow<List<CashEntry>> = db.cashDao().observeEntries(projectId)

    fun observePosition(projectId: Long): Flow<CashPosition> = combine(
        db.projectDao().observeById(projectId),
        db.expenseDao().observeForProject(projectId),
        db.cashDao().observePartners(projectId),
        db.cashDao().observeEntries(projectId)
    ) { project, expenses, partners, entries ->
        CashLedger.position(project?.projectValue ?: 0.0, expenses, partners, entries)
    }

    suspend fun getPartners(projectId: Long) = db.cashDao().getPartners(projectId)

    suspend fun savePartner(partner: ProjectPartner): Result<Unit> {
        if (partner.name.isBlank()) return Result.failure(IllegalArgumentException("Enter the partner's name."))
        if (partner.sharePercent !in 0.0..100.0) return Result.failure(IllegalArgumentException("Share must be between 0 and 100%."))
        val clean = partner.copy(name = partner.name.trim())
        if (clean.id == 0L) db.cashDao().insertPartner(clean) else db.cashDao().updatePartner(clean)
        return Result.success(Unit)
    }

    /** A partner with expenses or cash entries can't be removed (the money would vanish). */
    suspend fun deletePartner(partner: ProjectPartner): Result<Unit> = db.withTransaction {
        if (db.cashDao().expenseCount(partner.id) > 0 || db.cashDao().entryCount(partner.id) > 0) {
            return@withTransaction Result.failure(IllegalStateException(
                "${partner.name} has expenses or cash entries in this project. Change or delete those first."))
        }
        db.cashDao().deletePartner(partner)
        Result.success(Unit)
    }

    suspend fun record(projectId: Long, partnerId: Long, type: CashEntryType, amount: Double, date: Long, notes: String): Result<Unit> {
        if (amount <= 0) return Result.failure(IllegalArgumentException("Enter an amount above zero."))
        db.cashDao().insertEntry(CashEntry(projectId = projectId, partnerId = partnerId, type = type, amount = amount,
            date = date, notes = notes.trim(), enteredBy = CurrentDevice.id))
        return Result.success(Unit)
    }

    suspend fun deleteEntry(entry: CashEntry) = db.cashDao().deleteEntry(entry)
    suspend fun restoreEntry(entry: CashEntry) { db.cashDao().insertEntry(entry) }
}
