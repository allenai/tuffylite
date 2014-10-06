// For sbt 0.13, requires plugin addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.11.2")

import AssemblyKeys._ // put this at the top of the file

assemblySettings

// your assembly settings here
jarName := "tuffy.jar"

mainClass := Some("tuffy.main.Main")

