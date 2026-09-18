package androidx.room
import kotlin.reflect.KClass
annotation class Entity(val tableName: String = "", val indices: Array<Index> = [], val foreignKeys: Array<ForeignKey> = [], val primaryKeys: Array<String> = [])
annotation class PrimaryKey(val autoGenerate: Boolean = false)
annotation class ColumnInfo(val name: String = "", val collate: Int = 1) { companion object { const val NOCASE = 3 } }
annotation class ForeignKey(val entity: KClass<*>, val parentColumns: Array<String> = [], val childColumns: Array<String> = [], val onDelete: Int = 1, val onUpdate: Int = 1) { companion object { const val CASCADE = 5 } }
annotation class Index(vararg val value: String, val name: String = "", val unique: Boolean = false)
annotation class Fts4(val contentEntity: KClass<*> = Any::class, val tokenizer: String = "simple")
object FtsOptions { const val TOKENIZER_UNICODE61 = "unicode61" }
annotation class Embedded
annotation class Relation(val parentColumn: String, val entityColumn: String)
annotation class Dao
annotation class Query(val value: String)
annotation class Insert(val onConflict: Int = 3)
annotation class Upsert
annotation class Transaction
object OnConflictStrategy { const val IGNORE = 5 }
annotation class Database(val entities: Array<KClass<*>>, val version: Int, val exportSchema: Boolean = true)
abstract class RoomDatabase { class Builder<T> { fun openHelperFactory(factory: Any?): Builder<T> = this
    fun build(): T = TODO() } }
object Room { fun <T : RoomDatabase> databaseBuilder(context: android.content.Context, klass: Class<T>, name: String?): RoomDatabase.Builder<T> = TODO() }
