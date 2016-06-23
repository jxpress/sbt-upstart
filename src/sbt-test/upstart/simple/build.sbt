import net.jxpress.sbt.plugins.UpstartPlugin._

version := "0.1"

scalaVersion := "2.11.8"

name := "foo"

enablePlugins(UpstartPlugin)

prodHost := "127.0.0.1"

devHost := "127.0.0.2"

sshKeyFile := "~/.pem/hoge.pem"

slackPostURL := Some("https://xxx.yyy.com/xxx")

slackPostChannel := Some("#foo")