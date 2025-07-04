package com.example.transferdata.database.repository.dao

import androidx.room.Dao
import androidx.room.Query
import com.example.transferdata.database.model.RecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class RecordDAO : BaseDao<RecordEntity> {
    @Query("SELECT * FROM ${RecordEntity.TABLE_NAME}")
    abstract fun getAll(): Flow<List<RecordEntity>>

    @Query("UPDATE ${RecordEntity.TABLE_NAME} SET clockSkewSmartwatchNanos = :clockSkew WHERE id = :id")
    abstract suspend fun setClockSkew(id: Long, clockSkew: Long): Int

    @Query("UPDATE ${RecordEntity.TABLE_NAME} SET starRecordingNanos = :starRecordingNanos, starRecordingMilli = :starRecordingMilli WHERE id = :id")
    abstract suspend fun setStarRecordingTime(id: Long, starRecordingNanos: Long, starRecordingMilli: Long): Int

    @Query("UPDATE ${RecordEntity.TABLE_NAME} SET stopRecordingNanos = :stopRecordingNanos, stopRecordingMilli = :stopRecordingMilli WHERE id = :id")
    abstract suspend fun setStopRecordingTime(id: Long, stopRecordingNanos: Long, stopRecordingMilli: Long): Int

    @Query("SELECT * FROM ${RecordEntity.TABLE_NAME} WHERE id = :id")
    abstract suspend fun getById(id: Long): RecordEntity?
}