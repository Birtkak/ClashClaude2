import androidx.compose.ui.graphics.toComposeImageBitmap
import com.clashclaude.game.ui.Sprites
import org.jetbrains.skia.Image
import java.io.File

/** Loads the baked sprites from the app's assets folder, like the Android app does. */
fun installDesktopSprites() {
    val dir = File("../app/src/main/assets/sprites")
    Sprites.loader = { name ->
        File(dir, "$name.png").takeIf { it.exists() }?.let { Image.makeFromEncoded(it.readBytes()).toComposeImageBitmap() }
    }
}
