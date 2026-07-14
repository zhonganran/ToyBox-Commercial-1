package com.byd.wjx.data.repository

import com.byd.wjx.data.local.dao.PlaceDao
import com.byd.wjx.data.local.dao.RuleDao
import com.byd.wjx.data.local.entity.PlaceEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaceRepository @Inject constructor(
    private val placeDao: PlaceDao,
    private val ruleDao: RuleDao
) {
    fun getAll(): Flow<List<PlaceEntity>> = placeDao.getAll()
    suspend fun getAllSnapshot(): List<PlaceEntity> = placeDao.getAllSnapshot()
    suspend fun getById(id: Long): PlaceEntity? = placeDao.getById(id)
    suspend fun insert(place: PlaceEntity): Long = placeDao.insert(place)
    suspend fun update(place: PlaceEntity) = placeDao.update(place)
    suspend fun delete(place: PlaceEntity) = placeDao.delete(place)
    suspend fun getCount(): Int = placeDao.getCount()

    /** Returns how many automation rules reference this place (via place_enter/place_exit triggers). */
    suspend fun countRulesUsingPlace(placeId: Long): Int = ruleDao.countRulesUsingPlace(placeId)
}
