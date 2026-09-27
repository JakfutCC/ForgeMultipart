package codechicken.multipart.handler

import org.junit.Assert._
import org.junit.Test
import scala.collection.mutable.ArrayBuffer

class CubeWatchStateTest {
  @Test
  def packetHandlerLoadsWithoutCubicChunks(): Unit = {
    val loader = getClass.getClassLoader
    try {
      Class.forName(
        "com.cardinalstar.cubicchunks.world.cube.Cube",
        false,
        loader
      )
      fail("This test must run without the optional CubicChunks dependency")
    } catch {
      case _: ClassNotFoundException =>
    }
    Class.forName("codechicken.multipart.handler.MultipartSPH$", true, loader)
  }

  @Test
  def newWatchersGetOneSnapshotAndNoEarlierDeltas(): Unit = {
    val state = new CubeWatchState[ArrayBuffer[String]]
    val pos = (0, -1, 0)
    val serverParts = ArrayBuffer("old")
    val clientParts = ArrayBuffer[String]()
    serverParts += "new" // An add-part delta has been queued before the watch event.
    state.watch(pos, serverParts)
    state.watch(pos, serverParts)
    if (state.isWatching(pos)) clientParts += "new"
    var snapshots = 0
    state.sendPending { parts =>
      snapshots += 1
      clientParts ++= parts
    }
    assertEquals(1, snapshots)
    assertEquals(serverParts.toList, clientParts.toList)
    assertTrue(state.isWatching(pos))
    state.watch(pos, serverParts)
    state.sendPending(_ => snapshots += 1)
    assertEquals(1, snapshots)
  }

  @Test
  def unwatchCancelsPendingDescription(): Unit = {
    val state = new CubeWatchState[String]
    val pos = (1, 32, 2)
    state.watch(pos, "cube")
    state.unwatch(pos)
    state.sendPending(_ => fail("Unwatched cube was sent"))
    assertFalse(state.isWatching(pos))
  }

  @Test
  def rewatchRequiresAnotherSnapshotBeforeDeltas(): Unit = {
    val state = new CubeWatchState[String]
    val pos = (1, -2, 2)
    state.watch(pos, "old")
    state.sendPending(_ => ())
    state.unwatch(pos)
    state.watch(pos, "new")
    assertFalse(state.isWatching(pos))
    val sent = ArrayBuffer[String]()
    state.sendPending(sent += _)
    assertEquals(List("new"), sent.toList)
    assertTrue(state.isWatching(pos))
  }

  @Test
  def watchingOneHeightDoesNotSubscribeToOtherCubesOrPlayers(): Unit = {
    val first = new CubeWatchState[String]
    val second = new CubeWatchState[String]
    first.watch((0, -1, 0), "below")
    first.sendPending(_ => ())
    second.watch((0, 32, 0), "above")
    second.sendPending(_ => ())
    assertTrue(first.isWatching((0, -1, 0)))
    assertFalse(first.isWatching((0, 0, 0)))
    assertFalse(first.isWatching((0, 32, 0)))
    assertFalse(second.isWatching((0, -1, 0)))
    assertTrue(second.isWatching((0, 32, 0)))
  }

  @Test
  def columnUnwatchClearsPendingAndInitializedCubes(): Unit = {
    val state = new CubeWatchState[String]
    state.watch((1, -1, 2), "loaded")
    state.watch((2, -1, 2), "other column")
    state.sendPending(_ => ())
    state.watch((1, 32, 2), "pending")
    state.unwatchColumn(1, 2)
    assertFalse(state.isWatching((1, -1, 2)))
    assertTrue(state.isWatching((2, -1, 2)))
    state.sendPending(_ => fail("Unwatched column was sent"))
  }

  @Test
  def emptyCubeStillBecomesInitialized(): Unit = {
    val state = new CubeWatchState[String]
    val pos = (0, 0, 0)
    state.watch(pos, "empty")
    state.sendPending(_ =>
      ()
    ) // No description packet is necessary for an empty cube.
    assertTrue(state.isWatching(pos))
  }

  @Test
  def failedDescriptionDoesNotEnableUpdates(): Unit = {
    val state = new CubeWatchState[String]
    val pos = (0, 0, 0)
    state.watch(pos, "cube")
    try {
      state.sendPending(_ => throw new IllegalStateException("send failed"))
      fail("Expected failure")
    } catch {
      case _: IllegalStateException =>
    }
    assertFalse(state.isWatching(pos))
  }

  @Test
  def retryDoesNotRepeatAlreadySentDescriptions(): Unit = {
    val state = new CubeWatchState[String]
    state.watch((0, 0, 0), "first")
    state.watch((0, 1, 0), "second")
    val sent = ArrayBuffer[String]()
    try {
      state.sendPending { cube =>
        if (cube == "second") throw new IllegalStateException("send failed")
        sent += cube
      }
      fail("Expected failure")
    } catch {
      case _: IllegalStateException =>
    }
    state.sendPending(sent += _)
    assertEquals(List("first", "second"), sent.toList)
  }
}
