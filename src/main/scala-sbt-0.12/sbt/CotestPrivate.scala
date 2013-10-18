package sbt

import sbt.Tests._

// these access private[sbt]

object ForkCotest {
  def apply(frameworks: Seq[TestFramework], tests: List[TestDefinition], config: Execution, classpath: Seq[File], javaHome: Option[File], javaOpts: Seq[String], log: Logger): Task[Output] =
    ForkTests(frameworks, tests, config, classpath, javaHome, javaOpts, log)
}

object RunCotest {
  def apply(task: Task[Tests.Output], poolSize: Int): Tests.Output = {
    run(task, false, poolSize) match {
      case Value(result) => result
      case Inc(failure) => throw failure
    }
  }

  def run[T](task: Task[T], checkCycles: Boolean, poolSize: Int): Result[T] = {
    val (service, shutdown) = CompletionService[Task[_], Completed](poolSize)
    val x = new Execute[Task](checkCycles, Execute.noTriggers)(taskToNode)
    try { x.run(task)(service) } finally { shutdown() }
  }

  def taskToNode: NodeView[Task] = sbt.std.Transform.taskToNode(taskId)

  def taskId: Task ~> Task = new (Task ~> Task) {
    def apply[T](in: Task[T]): Task[T] = in
  }
}
