package codechicken.multipart.handler

import scala.collection.mutable

/** Pending cubes must not receive deltas until their initial description is
  * sent.
  */
private[handler] final class CubeWatchState[C] {
  private val pending = mutable.LinkedHashMap[(Int, Int, Int), C]()
  private val initialized = mutable.Set[(Int, Int, Int)]()

  def watch(pos: (Int, Int, Int), cube: C): Unit = {
    if (!initialized(pos)) pending.update(pos, cube)
  }

  def unwatch(pos: (Int, Int, Int)): Unit = {
    pending.remove(pos)
    initialized.remove(pos)
  }

  def unwatchColumn(x: Int, z: Int): Unit = {
    pending.retain((pos, _) => pos._1 != x || pos._3 != z)
    initialized.retain(pos => pos._1 != x || pos._3 != z)
  }

  def isWatching(pos: (Int, Int, Int)): Boolean = initialized(pos)

  def sendPending(send: C => Unit): Unit = {
    while (pending.nonEmpty) {
      val (pos, cube) = pending.head
      send(cube)
      initialized.add(pos)
      pending.remove(pos)
    }
  }
}
