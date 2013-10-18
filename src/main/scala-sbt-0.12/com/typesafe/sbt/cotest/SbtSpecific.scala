package com.typesafe.sbt
package cotest

import sbt._

object SbtSpecific {
  type Framework = org.scalatools.testing.Framework

  val EmptyResult = (TestResult.Passed, Map.empty[String, TestResult.Value])

  def createTestTask(input: SbtCotest.Input, tests: Seq[TestDefinition], runPolicy: Tests.TestRunPolicy, config: Tests.Execution, log: Logger): Task[Tests.Output] = {
    runPolicy match {
      case Tests.SubProcess(javaOpts) =>
        ForkCotest(input.frameworks.keys.toSeq, tests.toList, config, input.classpath.files, input.javaHome, javaOpts, log)
      case Tests.InProcess =>
        Tests(input.frameworks, input.loader, tests, config, log)
    }
  }
}
