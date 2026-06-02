package com.example.stationinspector.domain.repository

/**
 * Runs a block of repository operations atomically. Lets the presentation layer
 * compose multi-step writes transactionally without depending on Room/AppDatabase.
 */
interface TransactionRunner {
    suspend fun <R> runInTransaction(block: suspend () -> R): R
}
