package com.capsule.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.capsule.app.data.dao.AuditLogDao
import com.capsule.app.data.dao.ContinuationDao
import com.capsule.app.data.dao.ContinuationResultDao
import com.capsule.app.data.dao.IntentEnvelopeDao
import com.capsule.app.data.entity.AuditLogEntryEntity
import com.capsule.app.data.entity.ContinuationEntity
import com.capsule.app.data.entity.ContinuationResultEntity
import com.capsule.app.data.entity.IntentEnvelopeEntity
import com.capsule.app.data.security.KeystoreKeyProvider
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [
        IntentEnvelopeEntity::class,
        ContinuationEntity::class,
        ContinuationResultEntity::class,
        AuditLogEntryEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class OrbitDatabase : RoomDatabase() {

    abstract fun intentEnvelopeDao(): IntentEnvelopeDao
    abstract fun continuationDao(): ContinuationDao
    abstract fun continuationResultDao(): ContinuationResultDao
    abstract fun auditLogDao(): AuditLogDao

    companion object {
        private const val DB_NAME = "orbit.db"

        @Volatile
        private var INSTANCE: OrbitDatabase? = null

        /**
         * Opens the encrypted Room database. Must only be called from the :ml process.
         */
        fun getInstance(context: Context): OrbitDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): OrbitDatabase {
            System.loadLibrary("sqlcipher")
            val passphrase = KeystoreKeyProvider.getOrCreatePassphrase(context)
            val factory = SupportOpenHelperFactory(passphrase)

            return Room.databaseBuilder(
                context.applicationContext,
                OrbitDatabase::class.java,
                DB_NAME
            )
                .openHelperFactory(factory)
                .build()
        }
    }
}
