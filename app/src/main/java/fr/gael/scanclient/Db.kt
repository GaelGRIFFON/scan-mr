package fr.gael.scanclient

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/** Liste de départ des préfixes de magasin (modifiable dans l'app). */
val SEED_STORES = listOf(
    "ALESI", "BAYONN", "BORD", "CAEN", "CHAU", "GRENO", "HAVRE", "LEVAL", "LILLE", "MALES",
    "MULHO", "NANTE", "NIEL", "ORLE", "RENNE", "ROQUE", "SGEL", "STNAZA", "STRA", "TOUL",
    "LYON02", "LYON06",
)

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
    /** null = session en cours */
    val endedAt: Long? = null,
)

@Entity(tableName = "stores")
data class StoreEntity(
    @PrimaryKey val prefix: String,
    /** Nombre de chiffres du n° client ; null = 6 ou 7. */
    val clientLen: Int? = null,
)

@Entity(tableName = "scans", indices = [Index(value = ["sessionId", "full"], unique = true)])
data class ScanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val store: String,
    val client: String,
    val full: String,
    val ts: Long,
)

data class SessionRow(val id: Long, val name: String, val createdAt: Long, val endedAt: Long?, val scanCount: Int)

data class StoreCount(val store: String, val n: Int)

@Dao
interface ScanDao {
    // --- scans ---
    @Query("SELECT * FROM scans WHERE sessionId = :sessionId ORDER BY ts DESC, id DESC")
    fun observeScans(sessionId: Long): Flow<List<ScanEntity>>

    @Query("SELECT * FROM scans WHERE sessionId = :sessionId ORDER BY ts ASC, id ASC")
    suspend fun scansOf(sessionId: Long): List<ScanEntity>

    @Query("SELECT store AS store, COUNT(*) AS n FROM scans WHERE sessionId = :sessionId GROUP BY store ORDER BY store")
    fun observeRecap(sessionId: Long): Flow<List<StoreCount>>

    /** Renvoie -1 si le code existe déjà dans cette session (index unique). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertScan(scan: ScanEntity): Long

    /** Renvoie 0 si la modification créerait un doublon. */
    @Update(onConflict = OnConflictStrategy.IGNORE)
    suspend fun updateScan(scan: ScanEntity): Int

    @Query("DELETE FROM scans WHERE id = :id")
    suspend fun deleteScan(id: Long)

    @Query("DELETE FROM scans WHERE sessionId = :sessionId")
    suspend fun deleteScansOf(sessionId: Long)

    // --- sessions ---
    @Query(
        "SELECT s.id AS id, s.name AS name, s.createdAt AS createdAt, s.endedAt AS endedAt, " +
            "(SELECT COUNT(*) FROM scans WHERE sessionId = s.id) AS scanCount " +
            "FROM sessions s ORDER BY s.createdAt DESC",
    )
    fun observeSessions(): Flow<List<SessionRow>>

    @Query("SELECT * FROM sessions WHERE id = :id")
    fun observeSession(id: Long): Flow<SessionEntity?>

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun getSession(id: Long): SessionEntity?

    @Insert
    suspend fun insertSession(session: SessionEntity): Long

    @Query("UPDATE sessions SET endedAt = :time WHERE endedAt IS NULL")
    suspend fun endOpenSessions(time: Long)

    @Query("UPDATE sessions SET endedAt = :time WHERE id = :id")
    suspend fun endSession(id: Long, time: Long)

    @Query("UPDATE sessions SET endedAt = NULL WHERE id = :id")
    suspend fun reopenSession(id: Long)

    @Query("UPDATE sessions SET name = :name WHERE id = :id")
    suspend fun renameSession(id: Long, name: String)

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun deleteSession(id: Long)

    // --- magasins ---
    @Query("SELECT * FROM stores ORDER BY prefix")
    fun observeStores(): Flow<List<StoreEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStore(store: StoreEntity)

    @Query("DELETE FROM stores WHERE prefix = :prefix")
    suspend fun deleteStore(prefix: String)
}

@Database(entities = [ScanEntity::class, SessionEntity::class, StoreEntity::class], version = 2, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): ScanDao

    companion object {
        @Volatile private var instance: AppDb? = null

        private fun seed(db: SupportSQLiteDatabase) {
            SEED_STORES.forEach { db.execSQL("INSERT OR IGNORE INTO stores (prefix, clientLen) VALUES ('$it', NULL)") }
        }

        /** v1 (scans sans session) -> v2 : les anciens scans passent dans une session terminée "Session de test". */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS sessions (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, createdAt INTEGER NOT NULL, endedAt INTEGER)")
                db.execSQL("CREATE TABLE IF NOT EXISTS stores (prefix TEXT NOT NULL, clientLen INTEGER, PRIMARY KEY(prefix))")
                db.execSQL("CREATE TABLE IF NOT EXISTS scans_new (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, sessionId INTEGER NOT NULL, store TEXT NOT NULL, client TEXT NOT NULL, `full` TEXT NOT NULL, ts INTEGER NOT NULL)")

                val count = db.query("SELECT COUNT(*) FROM scans").use { if (it.moveToFirst()) it.getInt(0) else 0 }
                if (count > 0) {
                    val now = System.currentTimeMillis()
                    db.execSQL("INSERT INTO sessions (name, createdAt, endedAt) VALUES ('Session de test', $now, $now)")
                    db.execSQL(
                        "INSERT INTO scans_new (id, sessionId, store, client, `full`, ts) " +
                            "SELECT id, (SELECT MAX(id) FROM sessions), store, client, `full`, ts FROM scans",
                    )
                }
                db.execSQL("DROP TABLE scans")
                db.execSQL("ALTER TABLE scans_new RENAME TO scans")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_scans_sessionId_full ON scans (sessionId, `full`)")
                seed(db)
            }
        }

        fun get(context: Context): AppDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDb::class.java, "scans.db")
                .addMigrations(MIGRATION_1_2)
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) = seed(db)
                })
                .build().also { instance = it }
        }
    }
}
