package codechicken.multipart.handler

import com.cardinalstar.cubicchunks.api.IColumn
import com.cardinalstar.cubicchunks.api.event.CubeEvent
import com.cardinalstar.cubicchunks.world.cube.Cube
import codechicken.lib.vec.BlockCoord
import cpw.mods.fml.common.eventhandler.{EventPriority, SubscribeEvent}
import net.minecraft.entity.player.{EntityPlayer, EntityPlayerMP}
import net.minecraft.world.{ChunkCoordIntPair, World}
import net.minecraft.world.chunk.Chunk
import net.minecraftforge.common.MinecraftForge
import scala.collection.JavaConversions._
import scala.collection.mutable

object CubicChunksCompat {
  private case class PlayerWatches(
      player: EntityPlayerMP,
      world: World,
      cubes: CubeWatchState[Cube]
  )
  private val watchers = mutable.Map[Int, PlayerWatches]()

  def init(): Unit = MinecraftForge.EVENT_BUS.register(this)

  @SubscribeEvent(priority = EventPriority.HIGHEST)
  def onTileEntitiesLoad(event: CubeEvent.DataLoad): Unit = {
    val cube = event.cube
    val world = event.world

    MultipartSaveLoad.loadTiles(world, cube.cubeTileEntityMap)
  }

  @SubscribeEvent
  def onCubeWatch(event: CubeEvent.Watch): Unit = {
    val state = watchers.get(event.player.getEntityId) match {
      case Some(s) if (s.player eq event.player) && (s.world eq event.world) =>
        s
      case _ =>
        val s =
          PlayerWatches(event.player, event.world, new CubeWatchState[Cube])
        watchers.update(event.player.getEntityId, s)
        s
    }
    state.cubes.watch(
      (event.pos.getX, event.pos.getY, event.pos.getZ),
      event.cube
    )
  }

  @SubscribeEvent
  def onCubeUnWatch(event: CubeEvent.UnWatch): Unit = {
    watchers.get(event.player.getEntityId).foreach { state =>
      if ((state.player eq event.player) && (state.world eq event.world))
        state.cubes.unwatch((event.pos.getX, event.pos.getY, event.pos.getZ))
    }
  }

  def onColumnUnWatch(player: EntityPlayer, column: ChunkCoordIntPair): Unit = {
    watchers.get(player.getEntityId).foreach { state =>
      if ((state.player eq player) && (state.world eq player.worldObj))
        state.cubes.unwatchColumn(column.chunkXPos, column.chunkZPos)
    }
  }

  def onWorldUnload(world: World): Unit =
    watchers.retain((_, state) => state.world ne world)

  def pruneWatchers(players: Seq[EntityPlayerMP]): Unit = {
    val current = players.map(p => p.getEntityId -> p).toMap
    watchers.retain((id, state) =>
      current
        .get(id)
        .exists(p => (p eq state.player) && (p.worldObj eq state.world))
    )
  }

  def isWatching(player: EntityPlayerMP, pos: BlockCoord): Boolean =
    watchers
      .get(player.getEntityId)
      .exists(state =>
        (state.player eq player) && (state.world eq player.worldObj) &&
          state.cubes.isWatching((pos.x >> 4, pos.y >> 4, pos.z >> 4))
      )

  def sendPendingDescriptions(): Unit = {
    for (state <- watchers.values) {
      state.cubes.sendPending { cube =>
        val column = cube.getColumn[Chunk with IColumn]()
        val packet = MultipartSPH.getDescPacket(
          column,
          cube.getTileEntityMap.values.iterator
        )
        if (packet != null) packet.sendToPlayer(state.player)
      }
    }
  }
}
