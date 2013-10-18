# sbt-cotest

[sbt] plugin for running coordinated tests across projects.

Cotests are simply tests from multiple projects that run together as a single
test. This provides multi-jvm tests with more flexibility for writing and
configuring tests than possible with [sbt-multi-jvm]. For example, the parts of
the test can have different scala versions and dependencies, and any test
framework that sbt supports can be used.


## Plugin

This plugin requires sbt 0.12 or 0.13.

Add the sbt-cotest plugin to `project/plugins.sbt`. For example:

```scala
addSbtPlugin("com.typesafe.sbt" % "sbt-cotest" % "0.1.0")
```


## Configuration

Create an sbt project that serves as the aggregated test project, and separate
sbt projects for the test parts.

Add the `cotestSettings` to the aggregated project, passing the projects
containing the tests to be coordinated. For example:

```scala
lazy val cotests = project
  .settings(cotestSettings(a, b): _*)

lazy val a = project
  // settings ...

lazy val b = project
  // settings ...
```

## Cotests

Tests that run together as a cotest need to have the same fully qualified name.
The sbt-cotest plugin will collect tests with the same name from all the
projects specified with `cotestSettings`. It's not required that a cotest has a
corresponding part in each project. For example, one test could have five parts
(tests with the same name) across projects, and another test only two parts.

To run tests there are `cotest` and `cotestOnly` tasks. The `test` and
`testOnly` tasks are also overridden to point to these cotest variations.


## Contribution policy

Contributions via GitHub pull requests are gladly accepted from their original
author. Before we can accept pull requests, you will need to agree to the
[Typesafe Contributor License Agreement][cla] online, using your GitHub account.


## License

This code is open source software licensed under the [Apache 2.0 License]
[apache]. Feel free to use it accordingly.

[sbt]: https://github.com/sbt/sbt
[sbt-multi-jvm]: https://github.com/typesafehub/sbt-multi-jvm
[cla]: http://www.typesafe.com/contribute/cla
[apache]: http://www.apache.org/licenses/LICENSE-2.0.html
