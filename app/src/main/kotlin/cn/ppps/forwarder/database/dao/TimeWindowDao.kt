package cn.ppps.forwarder.database.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Transaction
import androidx.room.Update
import androidx.sqlite.db.SupportSQLiteQuery
import cn.ppps.forwarder.database.entity.TimeWindow
import io.reactivex.Single

@Dao
interface TimeWindowDao {

    @Insert
    fun insert(timeWindow: TimeWindow)

    @Delete
    fun delete(timeWindow: TimeWindow)

    @Query("DELETE FROM TimeWindow where id=:id")
    fun delete(id: Long)

    @Query("DELETE FROM TimeWindow")
    fun deleteAll()

    @Update
    fun update(timeWindow: TimeWindow)

    //TODO:允许主线程访问，后面再优化
    @Query("SELECT * FROM TimeWindow where id=:id")
    fun getOne(id: Long): TimeWindow?

    @Query("SELECT * FROM TimeWindow WHERE id IN (:ids)")
    fun getByIds(ids: List<Long>): List<TimeWindow>

    @Query("SELECT * FROM TimeWindow ORDER BY id ASC")
    fun pagingSource(): PagingSource<Int, TimeWindow>

    @Query("SELECT * FROM TimeWindow ORDER BY id ASC")
    fun getAll(): Single<List<TimeWindow>>

    @Transaction
    @RawQuery(observedEntities = [TimeWindow::class])
    fun getAllRaw(query: SupportSQLiteQuery): List<TimeWindow>

}
