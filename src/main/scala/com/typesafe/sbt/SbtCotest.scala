package com.typesafe.sbt

import com.typesafe.sbt.cotest.SbtSpecific._
import sbinary.DefaultProtocol.StringFormat
import sbt._
import sbt.Cache.seqFormat
import sbt.Keys._
import sbt.Project.Initialize
import scala.Console.{ BLUE, RESET }

object SbtCotest extends Plugin {
  case class Input(
    project: String,
    frameworks: Map[TestFramework, Framework],
    loader: ClassLoader,
    groups: Seq[Tests.Group],
    config: Tests.Execution,
    classpath: Classpath,
    javaHome: Option[File]
  )

  object CotestKeys {
    val cotestProjectName = TaskKey[String]("cotest-project-name")
    val cotestInput = TaskKey[Input]("cotest-input")
    val cotestInputs = TaskKey[Seq[Input]]("cotest-inputs")
    val cotestNames = TaskKey[Seq[String]]("cotest-names")
    val cotestExclusive = SettingKey[Boolean]("cotest-exclusive", "Run cotests exclusively (and use separate parallel task execution).")
    val cotest = TaskKey[Unit]("cotest", "Run all cotests across cotest projects.")
    val cotestOnly = InputKey[Unit]("cotest-only", "Run cotests provided as arguments.")
  }

  import CotestKeys._

  val CotestTask = Tags.Tag("cotest")

  def cotestSettings(projects: Project*) = {
    val cleanSettings = projects map { project => clean in project <<= clean in project triggeredBy clean }
    val defaultNameSetting = cotestProjectName <<= cotestProjectName or (thisProject map (_.id))
    val inputSetting = cotestInput <<= inputTask
    val inputSettings = projects flatMap { project => inScope(Scope(Select(project), Select(Test), This, This))(Seq(defaultNameSetting, inputSetting)) }
    cleanSettings ++ inputSettings ++ Seq(
      cotestInputs <<= (projects map { project => cotestInput in Test in project }).join,
      cotestNames <<= cotestInputs map collectNames storeAs cotestNames triggeredBy (compile in Test),
      compile in Test <<= cotestInputs map { _ => inc.Analysis.Empty },
      cotestExclusive := true,
      concurrentRestrictions in Global <++= exclusiveCotests,
      logBuffered in cotest := false,
      cotest <<= cotestTask,
      cotestOnly <<= cotestOnlyTask,
      test <<= cotest,
      testOnly <<= cotestOnly
    )
  }

  def inputTask: Initialize[Task[Input]] = {
   (cotestProjectName, loadedTestFrameworks, testLoader, testGrouping in test, testExecution in test, fullClasspath in test, javaHome in test) map Input
  }

  def collectNames(inputs: Seq[Input]): Seq[String] = {
    (inputs flatMap inputTestNames).distinct
  }

  def inputTestNames(input: Input): Seq[String] = {
    val tests = input.groups flatMap (_.tests)
    tests map (_.name)
  }

  def exclusiveCotests: Initialize[Seq[Tags.Rule]] = cotestExclusive { exclusive =>
    if (exclusive) Seq(Tags.exclusive(CotestTask)) else Seq.empty
  }

  def cotestTask: Initialize[Task[Unit]] = {
    (cotestNames, cotestInputs, cotestExclusive, streams in cotest, logBuffered in cotest) flatMap runCotestsTask
  }

  def cotestOnlyTask: Initialize[InputTask[Unit]] = {
    InputTask( Defaults.loadForParser(cotestNames)( (s, i) => Defaults.testOnlyParser(s, i getOrElse Nil) ) ) { parsed =>
      (parsed, cotestInputs, cotestExclusive, streams in cotestOnly, logBuffered in cotestOnly) flatMap {
        case ((selected, frameworkOptions), inputs, exclusive, s, buffered) =>
          val modifiedInputs = addFrameworkOptions(inputs, frameworkOptions)
          runCotestsTask(selected, modifiedInputs, exclusive, s, buffered)
      }
    }
  }

  def addFrameworkOptions(inputs: Seq[Input], frameworkOptions: Seq[String]): Seq[Input] = {
    inputs map { input => input.copy(config = input.config.copy(options = Tests.Argument(frameworkOptions: _*) +: input.config.options)) }
  }

  def runCotestsTask(testNames: Seq[String], inputs: Seq[Input], exclusive: Boolean, streams: TaskStreams, buffered: Boolean): Task[Unit] = {
    cotestsTask(testNames, inputs, exclusive, streams, buffered) map showResults(streams.log, inputs)
  }

  def cotestsTask(testNames: Seq[String], inputs: Seq[Input], exclusive: Boolean, streams: TaskStreams, buffered: Boolean): Task[Tests.Output] = {
    val column = maxProjectIdLength(inputs)
    val allNames = collectNames(inputs)
    val matchingNames = (allNames filter (test => testNames exists globMatch(test))).distinct
    val tasks = matchingNames map { test =>
      val testInputs = inputs filter { input => inputTestNames(input) contains test }
      val titleTask = testTitleTask(test, testInputs, streams.log, column)
      val cotasks = testInputs map { input => filteredTestTask(test, input, streams.log, buffered, column) }
      val testTask = Tests.foldTasks(cotasks, parallel = true)
      val titledTask = titleTask flatMap { _ => testTask }
      val cotestTask = if (exclusive) constant(RunCotest(titledTask, testInputs.size)) else titledTask
      cotestTask tag CotestTask
    }
    if (tasks.nonEmpty) Tests.foldTasks(tasks, parallel = false) else constant(EmptyResult)
  }

  def globMatch(testName: String)(expression: String): Boolean = {
    import java.util.regex.Pattern
    def quote(s: String) = if (s.isEmpty) "" else Pattern quote s
    val pattern = Pattern compile (expression split ("\\*", -1) map quote mkString ".*")
    (pattern matcher testName).matches
  }

  def testTitleTask(test: String, inputs: Seq[Input], log: Logger, column: Int): Task[Unit] = task {
    val blue = coloured(log, BLUE)
    log.info(logPrefix(Level.Info, "-" * column, column, blue("-")) + blue(test))
    log.debug("Cotest projects: " + projectList(inputs))
  }

  def filteredTestTask(testName: String, input: Input, logger: Logger, buffered: Boolean, column: Int): Task[Tests.Output] = {
    val log = wrapLogger(input.project, logger, column)
    val options = replaceTestLogger(input.config.options, log, buffered)
    val config = input.config.copy(options = options, tags = Seq.empty)
    val (tests, runPolicy) = filterTests(testName, input.groups)
    createTestTask(input, tests, runPolicy, config, log)
  }

  def filterTests(testName: String, groups: Seq[Tests.Group]): (Seq[TestDefinition], Tests.TestRunPolicy) = {
    val filtered = groups map { group => (group.tests filter (_.name == testName), group.runPolicy) }
    filtered find { _._1.nonEmpty } getOrElse (Seq.empty[TestDefinition], Tests.InProcess)
  }

  def showResults(log: Logger, inputs: Seq[Input])(output: Tests.Output): Unit = {
    Tests.showResults(log, output, noCotestsMessage(inputs))
  }

  def noCotestsMessage(inputs: Seq[Input]): String = {
    "No cotests to run for " + projectList(inputs)
  }

  def coloured(log: Logger, colour: String): String => String = {
    (text: String) => if (log.ansiCodesSupported) (colour + text + RESET) else text
  }

  def projectList(inputs: Seq[Input]): String = {
    inputs map (_.project) mkString ("[", ", ", "]")
  }

  def maxProjectIdLength(inputs: Seq[Input]): Int = {
    (inputs map (_.project.length)).max
  }

  def logPrefix(level: Level.Value, name: String, column: Int, separator: String): String = {
    val padding = column - name.length - level.toString.length + 5 // max level
    (" " * padding) + name + " " + separator + " "
  }

  def wrapLogger(name: String, wrapped: Logger, column: Int): Logger = new Logger {
    def trace(t: => Throwable): Unit = wrapped.trace(t)
    def success(message: => String): Unit = wrapped.success(message)
    def log(level: Level.Value, message: => String): Unit = wrapped.log(level, logPrefix(level, name, column, "|") + message)
    override def ansiCodesSupported: Boolean = wrapped.ansiCodesSupported
  }

  def replaceTestLogger(options: Seq[TestOption], log: Logger, buffered: Boolean): Seq[TestOption] = {
    options map {
      case Tests.Listeners(listeners) => Tests.Listeners(listeners map {
        case testLogger: TestLogger => new CotestLogger(log, buffered)
        case listener => listener
      })
      case option => option
    }
  }
}

class CotestLogger(log: Logger, buffered: Boolean) extends TestsListener {
  def startGroup(name: String): Unit = {}
  def testEvent(event: TestEvent): Unit = {}
  def endGroup(name: String, throwable: Throwable): Unit = {}
  def endGroup(name: String, result: TestResult.Value): Unit = {}
  def doInit(): Unit = {}
  def doComplete(finalResult: TestResult.Value): Unit = {}
  override def contentLogger(test: TestDefinition): Option[ContentLogger] = Some(TestLogger.contentLogger(log, buffered))
}
