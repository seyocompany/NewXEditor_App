@Database(entities = [ProjectEntity::class, ClipEntity::class, TextEntity::class], version = 4, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
}