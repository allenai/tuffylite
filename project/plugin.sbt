import sbt._

addSbtPlugin("com.github.mpeltonen" % "sbt-idea" % "1.5.1")

addSbtPlugin("com.github.gseitz" % "sbt-release" % "0.8")

addSbtPlugin("com.typesafe.sbt" % "sbt-scalariform" % "1.2.1")

addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "0.6.4")

// Revolver, for auto-reloading of changed files in sbt.
// See https://github.com/spray/sbt-revolver .
addSbtPlugin("io.spray" % "sbt-revolver" % "0.7.1")

lazy val ai2PluginsVersion = "0.2.2"

// Automates injection of artifact / git version info
addSbtPlugin("org.allenai.plugins" % "sbt-version-injector" % ai2PluginsVersion)

resolvers += "allenai nexus repository" at "http://utility.allenai.org:8081/nexus/content/repositories/releases"

credentials += Credentials("Sonatype Nexus Repository Manager", "utility.allenai.org", "deployment", "answermyquery")

addSbtPlugin("org.allenai.plugins" % "sbt-travis-publisher" % "0.2.2")

