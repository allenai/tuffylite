lazy val buildSettings = Seq(
  organization := "edu.stanford.hazy",
  description := "An internal version of Tuffy downloaded from: http://i.stanford.edu/hazy/tuffy/download/",
  publishMavenStyle := true,
  publishArtifact in (Compile, packageDoc) := false   // to avoid "javadoc: error - invalid flag: -target"
)

lazy val tuffy = Project(id = "tuffy-internal", base = file("."))
  .settings(buildSettings)
  .settings(PublishTo.ai2BintrayPublic)

resolvers ++= Seq(
  "scala-tools.org" at "http://scala-tools.org/repo-releases",
  "conjars" at "http://conjars.org/repo",
  "apache.releases" at "https://repository.apache.org/content/repositories/releases"
)

libraryDependencies ++= Seq(
  "args4j" % "args4j" % "2.0.16",
  "junit" % "junit" % "4.11",
  "org.antlr" % "antlr" % "3.2",
  "org.apache.commons" % "commons-lang3" % "3.3",
  "org.apache.commons" % "commons-math" % "2.2",
  "org.postgresql" % "postgresql" % "9.3-1102-jdbc41",
  "thirdparty" % "jgrapht-jdk1.6" % "0.8.2"
)

compileOrder := CompileOrder.JavaThenScala

javaOptions += "-Xmx4G"
scalacOptions ++= Seq("-Xlint", "-deprecation", "-feature")
