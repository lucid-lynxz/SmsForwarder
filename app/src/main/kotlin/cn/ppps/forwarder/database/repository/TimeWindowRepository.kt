package cn.ppps.forwarder.database.repository

import androidx.sqlite.db.SimpleSQLiteQuery
import cn.ppps.forwarder.database.dao.TimeWindowDao
import cn.ppps.forwarder.database.entity.TimeWindow
import io.reactivex.Single

class TimeWindowRepository(private val timeWindowDao: TimeWindowDao) {

    fun insert(timeWindow: TimeWindow) = timeWindowDao.insert(timeWindow)

    fun delete(id: Long) = timeWindowDao.delete(id)

    fun deleteAll() = timeWindowDao.deleteAll()

    fun update(timeWindow: TimeWindow) = timeWindowDao.update(timeWindow)

    fun getOne(id: Long) = timeWindowDao.getOne(id)

    fun getByIds(ids: List<Long>): List<TimeWindow> = timeWindowDao.getByIds(ids)

    fun getAllNonCache(): List<TimeWindow> {
        val query = SimpleSQLiteQuery("SELECT * FROM TimeWindow ORDER BY id ASC")
        return timeWindowDao.getAllRaw(query)
    }

    fun getAll(): Single<List<TimeWindow>> = timeWindowDao.getAll()

}
