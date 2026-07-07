import androidx.media3.transformer.*
import androidx.media3.common.*
fun test() {
    val items = listOf<EditedMediaItem>()
    val seq = EditedMediaItemSequence(items)
    val comp = Composition.Builder(seq).build()
}
