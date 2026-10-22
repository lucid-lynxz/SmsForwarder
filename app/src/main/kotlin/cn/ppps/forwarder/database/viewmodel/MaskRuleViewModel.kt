package cn.ppps.forwarder.database.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import cn.ppps.forwarder.database.dao.MaskRuleDao
import cn.ppps.forwarder.database.entity.MaskRule
import cn.ppps.forwarder.database.ext.ioThread
import kotlinx.coroutines.flow.Flow

class MaskRuleViewModel(private val dao: MaskRuleDao) : ViewModel() {

    val allMaskRule: Flow<PagingData<MaskRule>> = Pager(
        config = PagingConfig(
            pageSize = 10,
            enablePlaceholders = false,
            initialLoadSize = 10
        )
    ) { dao.pagingSource() }.flow.cachedIn(viewModelScope)

    fun insert(maskRule: MaskRule) = ioThread {
        dao.insert(maskRule)
    }

    fun update(maskRule: MaskRule) = ioThread {
        dao.update(maskRule)
    }

    fun delete(maskRule: MaskRule) = ioThread {
        dao.delete(maskRule)
    }
}
