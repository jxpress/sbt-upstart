organization  := "net.jxpress"
name          := "sbt-upstart"
version       := "0.0.1"
sbtPlugin     := true
scalacOptions ++= Seq("-feature", "-deprecation")

ScriptedPlugin.scriptedSettings
scriptedBufferLog  := false
scriptedLaunchOpts <+= version { "-Dplugin.version=" + _ }
watchSources       <++= sourceDirectory map { path => (path ** "*").get }

libraryDependencies ++= Seq(
  "org.scalatest" % "scalatest_2.11" % "2.2.1" % "test"
)

mavenRepositoryName := "mvnrepos"
gitHubURI := "https://github.com/jxpress/mvnrepos.git"
MvnReposOnGitHubPlugin.projectSettings

publishMavenStyle := true

publishArtifact in (Compile, packageBin) := true
publishArtifact in (Compile, packageDoc) := false
publishArtifact in (Compile, packageSrc) := false
publishArtifact in Test := false