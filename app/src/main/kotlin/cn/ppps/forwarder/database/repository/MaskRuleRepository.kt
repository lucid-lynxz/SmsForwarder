package cn.ppps.forwarder.database.repository

import androidx.sqlite.db.SimpleSQLiteQuery
import cn.ppps.forwarder.database.dao.MaskRuleDao
import cn.ppps.forwarder.database.entity.MaskRule
import cn.ppps.forwarder.utils.STATUS_ON
import io.reactivex.Single

class MaskRuleRepository(private val maskRuleDao: MaskRuleDao) {

    fun insert(maskRule: MaskRule) = maskRuleDao.insert(maskRule)

    fun delete(id: Long) = maskRuleDao.delete(id)

    fun deleteAll() = maskRuleDao.deleteAll()

    fun update(maskRule: MaskRule) = maskRuleDao.update(maskRule)

    fun getOne(id: Long) = maskRuleDao.getOne(id)

    fun getByIds(ids: List<Long>): List<MaskRule> = maskRuleDao.getByIds(ids).sortedBy { it.sort }

    /**
     * 按通道配置的 id 列表取出生效的打码规则，顺序由规则自身的 sort 决定（链式替换对顺序敏感）
     */
    fun getEnabledByCsv(ids: String): List<MaskRule> {
        val idList = ids.split(",").mapNotNull { it.trim().toLongOrNull() }
        if (idList.isEmpty()) return emptyList()
        return getByIds(idList).filter { it.enable == STATUS_ON && it.regex.isNotEmpty() }
    }

    fun getAllNonCache(): List<MaskRule> {
        val query = SimpleSQLiteQuery("SELECT * FROM MaskRule ORDER BY sort ASC, id ASC")
        return maskRuleDao.getAllRaw(query)
    }

    fun getAll(): Single<List<MaskRule>> = maskRuleDao.getAll()

}
