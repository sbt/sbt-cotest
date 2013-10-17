package sbt

import sbt.Tests._

/**
 * ForkTests is `private[sbt]`.
 */
object SbtCotestForkTests {
  def apply(frameworks: Seq[TestFramework], tests: List[TestDefinition], config: Execution, classpath: Seq[File], javaHome: Option[File], javaOpts: Seq[String], log: Logger): Task[Output] =
    ForkTests(frameworks, tests, config, classpath, javaHome, javaOpts, log)
}
