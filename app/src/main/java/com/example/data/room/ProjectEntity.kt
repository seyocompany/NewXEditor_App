@Query("SELECT * FROM texts WHERE projectId = :projectId ORDER BY orderIndex ASC")
fun getTextsForProject(projectId: String): Flow<List<TextEntity>>

@Insert(onConflict = OnConflictStrategy.REPLACE)
suspend fun insertText(text: TextEntity)

@Update
suspend fun updateText(text: TextEntity)

@Query("DELETE FROM texts WHERE id = :id")
suspend fun deleteTextById(id: String)

@Query("DELETE FROM texts WHERE projectId = :projectId")
suspend fun deleteTextsForProject(projectId: String)