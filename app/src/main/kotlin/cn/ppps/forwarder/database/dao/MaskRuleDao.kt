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
import cn.ppps.forwarder.database.entity.MaskRule
import io.reactivex.Single

@Dao
interface MaskRuleDao {

    @Insert
    fun insert(maskRule: MaskRule)

    @Delete
    fun delete(maskRule: MaskRule)

    @Query("DELETE FROM MaskRule where id=:id")
    fun delete(id: Long)

    @Query("DELETE FROM MaskRule")
    fun deleteAll()

    @Update
    fun update(maskRule: MaskRule)

    //TODO:允许主线程访问，后面再优化
    @Query("SELECT * FROM MaskRule where id=:id")
    fun getOne(id: Long): MaskRule?

    @Query("SELECT * FROM MaskRule WHERE id IN (:ids)")
    fun getByIds(ids: List<Long>): List<MaskRule>

    @Query("SELECT * FROM MaskRule ORDER BY sort ASC, id ASC")
    fun pagingSource(): PagingSource<Int, MaskRule>

    @Query("SELECT * FROM MaskRule ORDER BY sort ASC, id ASC")
    fun getAll(): Single<List<MaskRule>>

    @Transaction
    @RawQuery(observedEntities = [MaskRule::class])
    fun getAllRaw(query: SupportSQLiteQuery): List<MaskRule>

}
