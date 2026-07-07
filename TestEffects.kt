import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.effect.RgbFilter
import androidx.media3.effect.Contrast
import androidx.media3.effect.HslAdjustment

fun test(player: ExoPlayer) {
    player.setVideoEffects(listOf(
        Contrast(0.5f),
        HslAdjustment.Builder().adjustSaturation(1.5f).build()
    ))
}
