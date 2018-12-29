resolvers in ThisBuild ++= Seq(
  "Confluent Maven Repo" at "http://packages.confluent.io/maven/",
  Resolver.sonatypeRepo("releases"),
  Resolver.sonatypeRepo("public"),
  Resolver.sonatypeRepo("snapshots")
)