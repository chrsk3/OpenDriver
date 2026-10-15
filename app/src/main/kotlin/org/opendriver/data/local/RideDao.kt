package org.opendriver.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object (DAO) para operações na tabela de corridas do Room.
 */
@Dao
interface RideDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(ride: RideEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertSync(ride: RideEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rides: List<RideEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAllSync(rides: List<RideEntity>)

    @Query("SELECT * FROM rides ORDER BY timestamp DESC")
    suspend fun getAll(): List<RideEntity>

    @Query("SELECT * FROM rides ORDER BY timestamp DESC")
    fun getAllSync(): List<RideEntity>

    @Query("SELECT * FROM rides ORDER BY timestamp DESC")
    fun getAllFlow(): Flow<List<RideEntity>>

    @Query("SELECT * FROM rides ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<RideEntity>

    @Query("SELECT * FROM rides ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentSync(limit: Int): List<RideEntity>

    @Query("SELECT COUNT(*) FROM rides")
    suspend fun getCount(): Int

    @Query("SELECT COUNT(*) FROM rides")
    fun getCountSync(): Int

    @Query("DELETE FROM rides")
    suspend fun deleteAll()

    @Query("DELETE FROM rides")
    fun deleteAllSync()
}
