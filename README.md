# git !

Process Singularity events and sends a custom messages to Slack and a Deploy Report to NewRelic

![Slack](docs/img/slack.png)


![NewRelic](docs/img/newrelic.png)

Note: Project implemented using `scala cats` and `scala-effect` just to experiment with it.

# Release

```
sbt docker:publish
nmesos release singularity-broadcast -e dev -t 0.0.5-SNAPSHOT--force --dryrun false
nmesos release singularity-broadcast -e prod -t 0.0.5-SNAPSHOT--force --dryrun false
```

# Register the WebHook

`http://prod-singularity.uw2.nitro.us/singularity/webhooks` and `http://dev-singularity.uw2.nitro.us/singularity/webhooks` 

```
curl -i -X POST -H "Content-Type: application/json" -d@deploy_webhook_dev.json \
http://dev-singularity.uw2.nitro.us/singularity/api/webhooks
```

