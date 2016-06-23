package net.jxpress.sbt.plugins

import java.io.PrintWriter

import sbt._
import sbt.Keys._

object UpstartPlugin extends AutoPlugin {

  trait Generator {
    val description: String
    val author: String
    val startOn: Option[String] = Some("runlevel [2345]")
    val stopOn: Option[String] = Some("runlevel [!2345]")
    val doRespawn: Boolean = true
    val mainScript : String
    val preStartScript : Option[String] = None
    val preStopScript : Option[String] = None
    val postStartScript : Option[String] = None
    val postStopScript : Option[String] = None

    def apply(): String = {
      s"""
         |description "$description"
         |author "$author"
         |
         |${startOn.map { s => s"start on $s"}.getOrElse("")}
         |${stopOn.map {s => s"stop on $s"}.getOrElse("")}
         |
         |script
         |$mainScript
         |end script
         |
         |pre-start script
         |${preStartScript.getOrElse("")}
         |end script
         |
         |pre-stop script
         |${preStopScript.getOrElse("")}
         |end script
         |
         |post-stop script
         |${postStopScript.getOrElse("")}
         |end script
         |
         |post-start script
         |${postStartScript.getOrElse("")}
         |end script
         |
         |${if (doRespawn) "respawn" else ""}
        """.stripMargin
    }
  }

  trait Environment {
    val name: String
    lazy val confFileName : String = name.toLowerCase + ".conf"
    val hostName: String
  }

  case class Production(hostName: String) extends Environment {
    override val name = "PROD"
  }

  case class Development(hostName: String) extends Environment {
    override val name = "DEV"
  }

  object autoImport {
    lazy val Upstart = config("upstart").hide

    lazy val generate = taskKey[File]("Generate upstart script.")
    lazy val upload = taskKey[String]("Uploading the generated upstart script to the remote host. (default remotehost:/etc/init)")
    lazy val restart = taskKey[Unit]("Restart this deamon on the remote host.")

    lazy val prodHost = settingKey[String]("The host name for production serviceEnvironment.")
    lazy val devHost = settingKey[String]("The host name for development serviceEnvironment.")
    lazy val sshKeyFile = settingKey[File]("SSH key file")

    lazy val slackPostURL     = settingKey[Option[String]]("An url for notification in Upstart scripts")
    lazy val slackPostChannel = settingKey[Option[String]]("A channel for notification in Upstart scripts")
    lazy val serviceEnvironment = settingKey[Environment]("The serviceEnvironment to deploy (PROD/DEV) ")
    lazy val bashScriptJavaOptions = settingKey[Seq[String]]("")
    lazy val deployTo = settingKey[Option[Resolver]]("The repository to deploy ")
  }

  import autoImport._

  lazy val customScript = settingKey[Option[File]]("custom upstart script.")
  lazy val uploadLocation = settingKey[String]("location to upstart script: (default /etc/init)")

  lazy val serviceEnvVarName = settingKey[String](s"The name of the service environment variable: (default SERVICENAME_ENV")

  lazy val remoteRepos = settingKey[String]("A absolute path to the remote repository")
  lazy val deployPath = settingKey[String]("The name package root ")



  override lazy val projectSettings = inConfig(Upstart)(
    Seq(
      uploadLocation := "/etc/init",
      serviceEnvVarName := name.value.replaceAll("-", "_").toUpperCase + "_ENV",
      serviceEnvironment := { sys.env.getOrElse(serviceEnvVarName.value, "DEV") match {
        case "PROD" => Production(prodHost.value)
        case _ => Development(devHost.value)
      }},
      bashScriptJavaOptions := Seq(s"-J-Dconfig.resource=${serviceEnvironment.value.confFileName}"),
      remoteRepos := "/home/ubuntu/tgz",
      customScript := None,
      deployPath := s"${organization.value}.${name.value}".replaceAll("""\.""", """/"""),
      mappings in(Compile, packageDoc) := Seq(),
      deployTo := Some(Resolver.ssh("ssh-repos", serviceEnvironment.value.hostName, "tgz") as("ubuntu", sshKeyFile.value)),
      generate := {
        customScript.value match {
          case Some(f) =>
            f
          case _ =>
            generateTask.value
        }},
      upload := uploadTask.value,
      restart := rebootTask.value
    ))

  private def generateTask = Def.task {
    val env = serviceEnvironment.value
    def slackAlert(msg: String): String = {
      (slackPostURL.value, slackPostChannel.value) match {
        case (Some(url),Some(channel)) =>
          s"""exec curl -X POST --data-urlencode 'payload={"text":"[${env.name}] $msg", "channel": "$channel", "icon_emoji":":${name.value}:"}' "$url" """.stripMargin
        case _ => ""
      }
    }

    val log = streams.value.log
    log.info(s"Generate upstart script to ${name.value}.conf")

    val deployedPackage = s"${remoteRepos.value}/${deployPath.value}/${version.value}/${name.value}-${version.value}.tgz"
    val launcherScript = s"""env LANG="en_US.UTF-8" ./latest/bin/${name.value}"""
    val versioningName = s"${name.value}-${version.value}"

    val upstartScript = (new Generator {
      override val description: String = s"upstart script for $versioningName"
      override val author: String = organization.value
      override val mainScript: String =
        s"""
           |chdir /opt/${name.value}
           |rm -f -r latest && mkdir latest && tar xzvf $deployedPackage -C latest --strip-components 1
           |exec $launcherScript """.stripMargin

      override val preStartScript : Option[String] =
        Some(
          s"""
             |mkdir -p /opt/${name.value}/latest """.stripMargin)

      override val postStopScript =
        Some(
          s"""
             |${slackAlert(s"Stop $versioningName")} """.stripMargin)

      override val postStartScript =
        Some(
          s"""
             |${slackAlert(s"Start $versioningName")} """.stripMargin)
    })()

    val path = target.value / s"/${name.value}.conf"
    val pw = new PrintWriter(path)
    pw.write(upstartScript)
    pw.flush()
    pw.close()
    path
  }


  private def uploadTask = Def.task {
    val log = streams.value.log
    val remoteHost = serviceEnvironment.value.hostName
    log.info(s"Uploading the generated upstart script to $remoteHost:/etc/init")
    val script = generate.value
    val sshIdentity = s"-i ${sshKeyFile.value}"
    s"scp $sshIdentity $script ubuntu@$remoteHost:." !! log
    s"ssh $sshIdentity ubuntu@${serviceEnvironment.value.hostName} sudo cp ${name.value}.conf /etc/init/." !! log
    serviceEnvironment.value.hostName
  }

  private def rebootTask = Def.task {
    val log = streams.value.log
    val remoteHost = upload.value
    log.info(s"Re-start ${serviceEnvironment.value.name.toLowerCase} on the remote host: $remoteHost")
    val ssh = s"ssh -i ${sshKeyFile.value} ubuntu@$remoteHost"
    val cmd = s"$ssh sudo initctl"
    s"$cmd stop ${name.value}" ! log
    s"$cmd reload-configuration" ! log
    s"$cmd start ${name.value}" !! log
  }
}
