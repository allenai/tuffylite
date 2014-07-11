name := "tuffy-internal"

organization := "edu.wisc.hazy"

version := "0.3.1"

description := "An internal version of Tuffy available at: http://hazy.cs.wisc.edu/hazy/tuffy/"

Publish.settings

resolvers ++= Seq("AllenAI Snapshots" at "http://utility.allenai.org:8081/nexus/content/repositories/snapshots",
                        "AllenAI Releases" at "http://utility.allenai.org:8081/nexus/content/repositories/releases",
                        "Sonatype SNAPSHOTS" at "https://oss.sonatype.org/content/repositories/snapshots/",
                        "spray" at "http://repo.spray.io/",
                        "Typesafe Releases" at "http://repo.typesafe.com/typesafe/releases/")

resolvers ++= Seq("scala-tools.org" at "http://scala-tools.org/repo-releases",
                  "Sonatype SNAPSHOTS" at "https://oss.sonatype.org/content/repositories/snapshots/",
                  "conjars" at "http://conjars.org/repo",
                  "apache.releases" at "https://repository.apache.org/content/repositories/releases"
                )

libraryDependencies ++= Seq(
    "postgresql" % "postgresql" % "8.4-701.jdbc4",
    "org.antlr" % "antlr" % "3.2",
    "args4j" % "args4j" % "2.0.12",
    "org.apache.commons" % "commons-lang3" % "3.0",
    "thirdparty" % "jgrapht-jdk1.6" % "0.8.2",
    "org.apache.commons" % "commons-math" % "2.2",
    "junit" % "junit" % "4.9"
)

compileOrder := CompileOrder.JavaThenScala

javaOptions += "-Xmx4G"
