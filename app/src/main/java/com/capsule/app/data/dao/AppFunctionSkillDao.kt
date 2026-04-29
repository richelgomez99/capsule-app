package com.capsule.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.capsule.app.data.entity.AppFunctionSkillEntity

@Dao
interface AppFunctionSkillDao {

    /** Inserts a brand new skill. Conflicts (same `functionId`) abort. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(skill: AppFunctionSkillEntity)

    @Update
    suspend fun update(skill: AppFunctionSkillEntity)

    /**
     * Soft-supersede: replace the existing row when `functionId` collides.
     * Use this for schema bumps where the registry transactionally writes
     * the new schema and an `APPFUNCTION_REGISTERED` audit row.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(skill: AppFunctionSkillEntity)

    @Query("SELECT * FROM appfunction_skill WHERE functionId = :functionId LIMIT 1")
    suspend fun lookupLatest(functionId: String): AppFunctionSkillEntity?

    @Query(
        """
        SELECT * FROM appfunction_skill
        WHERE functionId = :functionId AND schemaVersion = :schemaVersion
        LIMIT 1
        """
    )
    suspend fun lookupExact(functionId: String, schemaVersion: Int): AppFunctionSkillEntity?

    @Query("SELECT * FROM appfunction_skill WHERE appPackage = :appPackage ORDER BY displayName ASC")
    suspend fun listForApp(appPackage: String): List<AppFunctionSkillEntity>

    @Query("SELECT * FROM appfunction_skill ORDER BY displayName ASC")
    suspend fun listAll(): List<AppFunctionSkillEntity>

    @Query("SELECT COUNT(*) FROM appfunction_skill")
    suspend fun count(): Int
}
