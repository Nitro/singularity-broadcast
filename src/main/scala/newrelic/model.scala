package newrelic
case class NewRelicApplicationList(applications: List[NewRelicId])

case class NewRelicId(id: Long)

case class NewRelicDeployResult(deployment: NewRelicId)