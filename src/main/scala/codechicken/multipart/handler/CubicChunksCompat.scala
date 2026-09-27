package codechicken.multipart.handler

import com.cardinalstar.cubicchunks.api.IColumn
import com.cardinalstar.cubicchunks.api.event.CubeEvent
import cpw.mods.fml.common.eventhandler.{EventPriority, SubscribeEvent}
import net.minecraft.world.chunk.Chunk
import net.minecraftforge.common.MinecraftForge
import scala.collection.JavaConversions._

object CubicChunksCompat {
  def init(): Unit = MinecraftForge.EVENT_BUS.register(this)

  @SubscribeEvent(priority = EventPriority.HIGHEST)
  def onTileEntitiesLoad(event: CubeEvent.DataLoad): Unit = {
    val cube = event.cube
    val world = event.world

    MultipartSaveLoad.loadTiles(world, cube.cubeTileEntityMap)
  }

  @SubscribeEvent
  def onCubeWatch(event: CubeEvent.Watch): Unit = {
    val cube = event.cube
    val column = cube.getColumn[Chunk with IColumn]()
    val packet = MultipartSPH.getDescPacket(column, cube.getTileEntityMap.values.iterator)
    if (packet != null) packet.sendToPlayer(event.player)
  }
}
