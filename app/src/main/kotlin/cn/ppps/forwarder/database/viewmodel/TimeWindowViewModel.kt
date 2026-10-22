package cn.ppps.forwarder.database.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import cn.ppps.forwarder.database.dao.TimeWindowDao
import cn.ppps.forwarder.database.entity.TimeWindow
import cn.ppps.forwarder.database.ext.ioThread
import kotlinx.coroutines.flow.Flow

class TimeWindowViewModel(private val dao: TimeWindowDao) : ViewModel() {

    val allTimeWindow: Flow<PagingData<TimeWindow>> = Pager(
        config = PagingConfig(
            pageSize = 10,
            enablePlaceholders = false,
            initialLoadSize = 10
        )
    ) { dao.pagingSource() }.flow.cachedIn(viewModelScope)

    fun insert(timeWindow: TimeWindow) = ioThread {
        dao.insert(timeWindow)
    }

    fun update(timeWindow: TimeWindow) = ioThread {
        dao.update(timeWindow)
    }

    fun delete(timeWindow: TimeWindow) = ioThread {
        dao.delete(timeWindow)
    }
}
