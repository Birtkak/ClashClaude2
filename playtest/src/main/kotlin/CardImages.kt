// Renders every card exactly as it looks in the game's hand, for the Card Forge page.
// Usage: ../gradlew cardImages  ->  build/card-images/<id>.png

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import com.clashclaude.game.data.Cards
import com.clashclaude.game.ui.CardTile
import com.clashclaude.game.ui.ClashTheme
import org.jetbrains.skia.EncodedImageFormat
import java.io.File

fun main(args: Array<String>) {
    val out = File(args.getOrElse(0) { "build/card-images" }).apply { mkdirs() }
    for (card in Cards.all) {
        val scene = ImageComposeScene(234, 300, Density(2f)) {
            ClashTheme { CardTile(card, Modifier.fillMaxSize()) }
        }
        scene.render(0).use { img ->
            File(out, "${card.id}.png").writeBytes(img.encodeToData(EncodedImageFormat.PNG)!!.bytes)
        }
        scene.close()
    }
    println("wrote ${Cards.all.size} card images to $out")
}
