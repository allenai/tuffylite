scalacOptions ++= Seq("-Xlint", "-deprecation", "-feature")

// This is required until our allenai-sbt-core-settings plugin is added
// to https://bintray.com/sbt/sbt-plugin-releases (request pending)
resolvers += Resolver.url("bintray-allenai-sbt-plugin-releases",
  url("http://dl.bintray.com/content/allenai/sbt-plugins"))(Resolver.ivyStylePatterns)

lazy val ai2PluginsVersion = "2014.10.21-0"

// will be added to all projects automatically:
addSbtPlugin("org.allenai.plugins" % "allenai-sbt-core-settings" % ai2PluginsVersion)

// must be 'enabled' for projects:
addSbtPlugin("org.allenai.plugins" % "allenai-sbt-webapp" % ai2PluginsVersion)

addSbtPlugin("org.allenai.plugins" % "allenai-sbt-web-service" % ai2PluginsVersion)
