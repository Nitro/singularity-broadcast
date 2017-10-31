package slack

import cats.effect.IO
import com.typesafe.scalalogging.LazyLogging
import conf.Conf
import io.circe.Json
import io.circe.parser._
import singularity._
import util.JsonUtil._
import util.{HttpIO, QuoteRandom}

/**
  * https://api.slack.com/docs/message-formatting
  */
class SlackHandlerImpl(conf: Conf, io: HttpIO) extends LazyLogging {

  val BaseJson = parse(conf.slack.baseJson).right
    .getOrElse(sys.error("Unable to parse Slack baseJson json"))

  def footer() = {
    val (icon, sentence) = QuoteRandom.nextQuote()
    s"""
       |  "footer": "$icon $sentence",
        """.stripMargin

  }

  def send(deployEvent: DeployEvent, message: Json): IO[Unit] = {
    // Send only finished deployments to the important channel (#allops)
    val targetChannel = deployEvent match {
      case DeployFinished(info) if info.isProd => conf.slack.channel.important
      case DeployStarting(_) | DeployFinished(_) | IgnoredEvent =>
        conf.slack.channel.info
    }
    val msgWithChannel = message.deepMerge(json {
      s"""{
          "channel": "$targetChannel"
          }
        """
    })

    io.postJsonIgnoreResult(conf.slack.url, msgWithChannel).map(_ => ())
  }

  def buildMessage(deploy: DeployEvent,
                   newRelicDeployId: Option[Long]): Json = {
    deploy match {
      case DeployStarting(info) =>
        json {
          s"""{
                 "text": "Deploying ${info.prettyServiceName} to ${info.prettyEnvironment}"
          }
           """
        }.deepMerge(BaseJson)
          .deepMerge(attachments(info, newRelicDeployId))
      case DeployFinished(info) if info.isProd =>
          json {
          s"""{
                "text": "${info.prettyStatus} deploying <${info.singularityRequestUrl}|${info.serviceName}:${info.newImageTag}> to ${info.prettyEnvironment}, info at <${conf.slack.channel.infoLink}|${conf.slack.channel.info}>"
          }
          """
          }.deepMerge(BaseJson)
      case DeployFinished(info) =>
        json {
          s"""{
                "text": "${info.prettyStatus} deploying <${info.singularityRequestUrl}|${info.serviceName}:${info.newImageTag}> to ${info.prettyEnvironment}"
          }
           """
        }.deepMerge(BaseJson)
      case IgnoredEvent => Json.Null
    }
  }

  def attachments(info: DeployInfo, newRelicDeployId: Option[Long]): Json = {

    val previousDeployIdField = info.previousDeployId.map { deployId =>
      s"""
         |{
         |  "title": "Previous DeployId",
         |  "value": "<${info.singularityPreviousDeployId.get}|$deployId>",
         |  "short": true
         |}
       """.stripMargin
    }

    val gitHubLink = if (info.isTagAValidVersion) {
      s"<https://github.com/Nitro/${info.gitHubRepoName}/releases/tag/v${info.newImageTag}|${info.newImageTag}>"
    } else {
      s"${info.newImageTag}"
    }

    val dockerLink =
      s"<https://hub.docker.com/r/gonitro/${info.imageName}/tags|(Docker Repo)>"

    val versionField = Some(s"""
         |{
         |  "title": "Version",
         |  "value": "$gitHubLink $dockerLink",
         |  "short": true
         |}
       """.stripMargin)

    val deployField = Some(
      s"""
         |{
         |  "title": "DeployId",
         |  "value": "<${info.singularityNewDeployUrl}|${info.newDeployId}>",
         |  "short": true
         |}
       """.stripMargin
    )

    val lastDeployTime = info.previousLastDeployTime.map(time => s"""
         |{
         |  "title": "Previous version was deployed",
         |  "value": "$time",
         |  "short": true
         |}
       """.stripMargin)

    val prodVersion = info.prodImageTag.map(prodVersion => s"""
         |{
         |  "title": "Version in Prod",
         |  "value": "<${info.singularityProdRequestUrl.get}|$prodVersion>",
         |  "short": true
         |}
       """.stripMargin)

    val prodLastDeployTime = info.prodLastDeployTime.map(time => s"""
         |{
         |  "title": "Prod haven't been update since",
         |  "value": "${if (info.warningProdLastDeploy) "⚠" else "✅"} $time",
         |  "short": true
         |}
       """.stripMargin)

    val versionDiff = info.previousImageTag.flatMap { previousImageTag =>
      if (!info.isTagAValidVersion) {
        None
      } else {
        Some(
          s"""
             |{
             |  "title": "Previous version (diff)",
             |  "value": "<https://github.com/Nitro/${info.gitHubRepoName}/compare/v${previousImageTag}...v${info.newImageTag}|${previousImageTag}...${info.newImageTag}>",
             |  "short": true
             |}
           """.stripMargin
        )
      }
    }

    val newRelicTrack = for {
      newRelicAppId <- info.newRelicAppId
      deployId <- newRelicDeployId
    } yield {
      s"""
         |{
         |  "title": "NewRelic deploy:",
         |  "value": "<https://rpm.newrelic.com/accounts/${conf.newRelic.accountId}/applications/$newRelicAppId/deployments/$deployId|New Relic APM>",
         |  "short": true
         |}
           """.stripMargin
    }

    val fields = Seq(versionField,
                     deployField,
                     newRelicTrack,
                     previousDeployIdField,
                     versionDiff,
                     lastDeployTime,
                     prodVersion,
                     prodLastDeployTime).flatten.mkString(",")

    val versionInfo =
      s"""
         |{
         |              "fallback": "Deploying ${info.prettyServiceName} (to ${info.prettyEnvironment})",
         |              "color": "good",
         |              ${footer()}
         |              "fields": [ $fields ]
         |}
      """.stripMargin

    val changes = info.diffEnvironmentConf.map {
      case DiffEnvironmentConf(Added, key, value, _) => s" ➕ $key: `$value` "
      case DiffEnvironmentConf(Removed, key, value, _) =>
        s" ❌ $key: ~'$value'~ "
      case DiffEnvironmentConf(Changed, key, value, Some(oldValue)) =>
        s"""❗ $key: ~'$oldValue'~ \\n  ➝ `$value`""".stripMargin
    }

    val envInfo =
      if (changes.isEmpty)
        None
      else {
        val text =
          changes.mkString("\\n")
        Some(
          s"""
             |{
             |  "title": "Environment changes since last deployment:",
             |  "color": "warning",
             |  "mrkdwn_in": ["text"],
             |  "text": "$text"
             |}
        """.stripMargin
        )
      }

    val attachments =
      Seq(Some(versionInfo), envInfo).flatten.mkString(",")

    json {
      s"""{"attachments": [$attachments]}"""
    }
  }
}
