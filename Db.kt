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
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "scans", indices = [Index(value = ["full"], unique = true)])
data class ScanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val store: String,
    val client: String,
    val full: String,
    val ts: Long,
)

@Dao
interface ScanDao {
    @Query("SELECT * FROM scans ORDER BY ts DESC, id DESC")
    fun observeScans(): Flow<List<ScanEntity>>

    /** La liste des magasins connus est dérivée des scans : corriger/supprimer un scan corrige l'apprentissage. */
    @Query("SELECT DISTINCT store FROM scans")
    fun observeStores(): Flow<List<String>>

    @Query("SELECT * FROM scans ORDER BY ts ASC")
    suspend fun all(): List<ScanEntity>

    /** Renvoie -1 si le code existe déjà (index unique). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(scan: ScanEntity): Long

    /** Renvoie 0 si la modification créerait un doublon. */
    @Update(onConflict = OnConflictStrategy.IGNORE)
    suspend fun update(scan: ScanEntity): Int

    @Query("DELETE FROM scans WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM scans")
    suspend fun clear()
}

@Database(entities = [ScanEntity::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): ScanDao

    companion object {
        @Volatile private var instance: AppDb? = null

        fun get(context: Context): AppDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDb::class.java, "scans.db")
                .build().also { instance = it }
        }
    }
}
