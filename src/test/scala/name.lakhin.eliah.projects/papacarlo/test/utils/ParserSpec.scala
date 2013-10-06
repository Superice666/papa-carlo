/*
   Copyright 2013 Ilya Lakhin (Илья Александрович Лахин)

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/
package name.lakhin.eliah.projects
package papacarlo.test.utils

import name.lakhin.eliah.projects.papacarlo.{Syntax, Lexer}
import org.scalatest.FunSpec
import net.liftweb.json.JsonAST._

abstract class ParserSpec(parserName: String,
                          lexerConstructor: () => Lexer,
                          syntaxConstructor: Lexer => Syntax)
  extends FunSpec {

  private val monitors:
    Map[String, (String, () => Monitor)] = Map(
      "token" ->
        (
          "tokenize",
          () => new TokenizerMonitor(lexerConstructor)
        ),
      "fragment" ->
        (
          "produce fragments",
          () => new FragmentationMonitor(lexerConstructor)
        ),
      "cache" ->
        (
          "track cache",
          () => new CacheMonitor(lexerConstructor, syntaxConstructor)
        ),
      "node" ->
        (
          "produce syntax nodes",
          () => new NodeMonitor(lexerConstructor, syntaxConstructor)
        ),
      "error" ->
        (
          "produce syntax errors",
          () => new ErrorMonitor(lexerConstructor, syntaxConstructor)
        )
    )

  private val tests =
    Resources.json[Map[String, Map[String, JValue]]](parserName, "config.json")
      .map({
        case (testName, settings) =>
          Test(
            parserName = parserName,

            testName = testName,

            steps = settings
              .get("steps")
              .flatMap {
                case JInt(value) => Some(value.toInt)
                case _ => None
              }
              .getOrElse((0 until 100)
                .find(step => !Resources.exist(
                  parserName + "/" + testName + "/input",
                  "step" + step + ".txt"
                ))
                .getOrElse(100)),

            monitors = settings.get("monitors").flatMap {
              case JArray(monitors: List[JValue]) =>
                Some(monitors.flatMap {
                  case JString(monitor) => Some(monitor)
                  case _ => None
                }.toSet)

              case _ => None
            }.getOrElse(monitors.keys.toSet),

            shortOutput = settings
              .get("shortOutput")
              .exists(_ == JBool(value = true)),

            outputFrom = settings
              .get("outputFrom")
              .flatMap {
                case JInt(value) => Some(value.toInt)
                case _ => None
              }
              .getOrElse(0)
          )
      })

  for (test <- tests) {
    describe(test.testName + " test") {

      for ((monitorName, (description, monitorConstructor)) <- monitors)
        if (test.monitors.contains(monitorName))
          it("should " + description) {
            val monitor = monitorConstructor()
            monitor.shortOutput = test.shortOutput

            var statistics = List.empty[Long]
            var results = List.empty[String]

            for (step <- 0 until test.steps) {
              monitor.prepare()
              statistics ::= monitor.input(test.inputs.getOrElse(step, ""))
              val result = monitor.getResult
              if (step >= test.outputFrom)
                test.write(monitorName, step, result)
              results ::= result
            }

            Resources.update(
              parserName + "/" + test.testName + "/statistics",
              monitorName + ".txt",
              statistics.reverse.zipWithIndex.map {
                case (time, step) => "Step " + step + ": " + time + "ms"
              }.mkString("\n")
            )

            for ((result, step) <- results.reverse.zipWithIndex)
              if (step >= test.outputFrom)
                assert(
                  result == test.prototypes.get(monitorName)
                    .flatMap(_.get(step)).getOrElse(""),
                  "Step " + step + " result did not equal to the prototype"
                )
          }
    }
  }
}