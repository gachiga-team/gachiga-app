package com.gachiga.app.data.trip

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TripDao {
    /** 진행 중(ACTIVE 또는 PAUSED)인 여행. 앱을 다시 열어도 이어지게 한다 (F1). */
    @Query("SELECT * FROM Trip WHERE status != 'ENDED' ORDER BY startedAt DESC LIMIT 1")
    fun observeOngoingTrip(): Flow<Trip?>

    @Query("SELECT * FROM Trip WHERE status = 'ENDED' ORDER BY startedAt DESC")
    fun observeEndedTrips(): Flow<List<Trip>>

    @Query("SELECT * FROM Trip WHERE status != 'ENDED' ORDER BY startedAt DESC LIMIT 1")
    suspend fun ongoingTrip(): Trip?

    @Query("SELECT * FROM Trip WHERE id = :id")
    suspend fun tripById(id: Long): Trip?

    @Insert suspend fun insertTrip(trip: Trip): Long
    @Update suspend fun updateTrip(trip: Trip)

    @Query("SELECT * FROM Segment WHERE tripId = :tripId ORDER BY startedAt")
    suspend fun segments(tripId: Long): List<Segment>

    @Query("SELECT * FROM Segment WHERE tripId = :tripId AND endedAt IS NULL LIMIT 1")
    suspend fun openSegment(tripId: Long): Segment?

    @Insert suspend fun insertSegment(segment: Segment): Long
    @Update suspend fun updateSegment(segment: Segment)

    @Query("SELECT * FROM Refuel WHERE tripId = :tripId ORDER BY detectedAt")
    suspend fun refuels(tripId: Long): List<Refuel>

    @Insert suspend fun insertRefuel(refuel: Refuel): Long
    @Update suspend fun updateRefuel(refuel: Refuel)

    @Query("SELECT * FROM Expense WHERE tripId = :tripId ORDER BY createdAt")
    suspend fun expenses(tripId: Long): List<Expense>

    @Insert suspend fun insertExpense(expense: Expense): Long
}
