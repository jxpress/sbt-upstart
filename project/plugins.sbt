libraryDependencies <+= sbtVersion( sv => "org.scala-sbt" % "scripted-plugin" % sv)

resolvers += "Maven Repository on Github" at "https://jxpress.github.io/mvnrepos/"

addSbtPlugin("net.jxpress" % "sbt-ghp-repos" % "0.0.2")