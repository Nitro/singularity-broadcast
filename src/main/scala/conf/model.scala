package conf

case class Conf(singularity: SingularityConf,
                slack: SlackConfig,
                newRelic: NewRelicConf)

case class SingularityConf(prodUrl: String, devUrl: String)

case class SlackChannel(important: String, info: String, infoLink: String)

case class SlackConfig(url: String, baseJson: String, channel: SlackChannel)

case class NewRelicConf(apiKey: String, accountId: Long)
