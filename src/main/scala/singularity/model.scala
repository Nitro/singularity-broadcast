package singularity



case class SingularityContainerInfo(docker: SingularityDockerInfo)

case class SingularityDeploy(
    requestId: String,
    id: String,
    containerInfo: SingularityContainerInfo
)

case class SingularityDockerInfo(
    image: String,
    dockerParameters: Seq[SingularityDockerParameter]
) {

  val ServiceNamePattern = "ServiceName="
  val EnvironmentPattern = "EnvironmentName="

  def imageName = image.split(":").head.split("/").last

  def imageTag = image.split(":").last // docker version as in

  // for instances label: ServiceName=play-nitro-scala
  def serviceName =
    dockerParameters
      .filter(_.key == "label")
      .find(_.value.startsWith(ServiceNamePattern))
      .map(_.value.drop(ServiceNamePattern.size))
      .getOrElse("<unknown>")

  def environment: Option[String] =
    dockerParameters
      .filter(_.key == "label")
      .find(_.value.startsWith(EnvironmentPattern))
      .map(_.value.drop(EnvironmentPattern.size))


}

case class SingularityDockerParameter(
    key: String,
    value: String
)

case class SingularityRequestDeployState(
    activeDeploy: Option[SingularityDeployMarker],
    pendingDeploy: Option[SingularityDeployMarker])

case class SingularityRequestParent(
    requestDeployState: Option[SingularityRequestDeployState],
    activeDeploy: Option[SingularityDeploy] = None
) {
  def version: Option[String] =
    activeDeploy.map(_.containerInfo.docker.imageTag)
}

case class SingularityDeployResult(deployState: String) {}

case class SingularityDeployMarker(requestId: String,
                                   deployId: String,
                                   timestamp: Long) {}

case class SingularityDeployUpdate(
    deployMarker: SingularityDeployMarker,
    deploy: SingularityDeploy,
    deployResult: Option[SingularityDeployResult],
    eventType: String) {
  def status: String =
    deployResult
      .map(_.deployState.toLowerCase().capitalize)
      .getOrElse("<unknown state>")
}

trait DeployEvent

case class DeployStarting(info: DeployInfo) extends DeployEvent

case class DeployFinished(info: DeployInfo) extends DeployEvent

case object IgnoredEvent extends DeployEvent

case class DeployInfo(serviceName: String,
                      imageName: String,
                      newRelicAppName: Option[String],
                      newImageTag: String,
                      newDeployId: String,
                      previousDeployId: Option[String],
                      previousLastDeployTime: Option[String],
                      prodLastDeployTime: Option[String],
                      warningProdLastDeploy: Boolean,
                      environment: Option[String],
                      diffEnvironmentConf: List[DiffEnvironmentConf],
                      status: String,
                      singularityRequestUrl: String,
                      singularityNewDeployUrl: String,
                      singularityPreviousDeployId: Option[String],
                      //singularityPreviousDeployUrl: Option[String] = None,
                      singularityProdRequestUrl: Option[String] = None,
                      previousImageTag: Option[String] = None,
                      prodImageTag: Option[String] = None,
                      newRelicAppId: Option[Long] = None) {

  def prettyServiceName =
    serviceName.replaceAll("_", " ").replaceAll("-", " ").toLowerCase.capitalize

  def prettyEnvironment = environment.getOrElse("<unknown>").toLowerCase.capitalize

  def prettyStatus = {
    val statusText = status.toLowerCase.capitalize
    val icon = if (statusText == "Succeeded") "✅" else "⚠"
    s"$icon $statusText"
  }

  /**
    * used to guess the github repo.
    * nitro-accounts-my-account => https://github.com/Nitro/nitro-accounts
    * user-service-server => https://github.com/Nitro/user-service
    *
    */
  val CustomRemoveToGithub = Seq("-my-account", "-server", "-admin")

  /**
    * Guess github repo
    */
  def gitHubRepoName: String = {
    CustomRemoveToGithub.foldLeft(imageName) {
      case (name, toRemove) =>
        name.replace(toRemove, "")
    }
  }

  // :( Some docker tags doesn't match with a valid version (released on Github)
  def isTagAValidVersion = newImageTag.matches("(\\d+)\\.(\\d+)\\.(\\d+).*")

  def isProd = environment.exists(_.toLowerCase() == "prod")

}

case class SingularityDeployHistory(
    deploy: SingularityDeploy,
    deployResult: Option[SingularityDeployResult] = None,
    deployMarker: Option[SingularityDeployMarker] = None
)

trait ChangeType

case object Added extends ChangeType

case object Removed extends ChangeType

case object Changed extends ChangeType

case class DiffEnvironmentConf(changeType: ChangeType,
                               key: String,
                               value: String,
                               previousValue: Option[String] = None)
