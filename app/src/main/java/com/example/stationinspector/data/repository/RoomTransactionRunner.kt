package com.example.stationinspector.data.repository

import androidx.room.withTransaction
import com.example.stationinspector.data.local.AppDatabase
import com.example.stationinspector.domain.repository.TransactionRunner
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomTransactionRunner @Inject constructor(
    private val db: AppDatabase
) : TransactionRunner {
    override suspend fun <R> runInTransaction(block: suspend () -> R): R =
        db.withTransaction { block() }
}
