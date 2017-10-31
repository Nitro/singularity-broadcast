package util

import singularity.SingularityDeployUpdate

object Cache {

  var cache = List.empty[String]

  val MaxSize = 50

  private def toId(deploy: SingularityDeployUpdate) = s"${deploy.eventType}-${deploy.deployMarker.requestId}-${deploy.deployMarker.deployId}"

  def isAlreadySeen(deploy: SingularityDeployUpdate) = cache.contains(toId(deploy))

  def remember(deploy: SingularityDeployUpdate): Unit = {
    cache = toId(deploy) :: cache.take(MaxSize)
  }
}