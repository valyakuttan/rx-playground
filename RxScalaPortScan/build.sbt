name := "RxScalaPortScan"

version := "1.0"

scalaVersion := "2.10.2"

resolvers += "Local Maven Repository" at "file://"+Path.userHome.absolutePath+"/.m2/repository"

val rxVersion = "0.14.2-SNAPSHOT"

libraryDependencies ++= Seq(
	"com.netflix.rxjava" % "rxjava-scala" % rxVersion
)


