package com.typesafe.sbt
package cotest

import sbt._

object SbtSpecific {
  type Framework = sbt.testing.Framework

  val EmptyResult = Tests.Output(TestResult.Passed, Map.empty[String, SuiteResult], Iterable.empty)

  def createTestTask(input: SbtCotest.Input, tests: Seq[TestDefinition], runPolicy: Tests.TestRunPolicy, config: Tests.Execution, log: Logger): Task[Tests.Output] = {
    val runners = Defaults.createTestRunners(input.frameworks, input.loader, config)
    runPolicy match {
      case Tests.SubProcess(options) =>
        ForkCotest(runners, tests.toList, config, input.classpath.files, options, log)
      case Tests.InProcess =>
        Tests(input.frameworks, input.loader, runners, tests, config, log)
    }
  }
}
