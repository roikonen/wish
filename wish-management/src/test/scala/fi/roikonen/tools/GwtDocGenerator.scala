package fi.roikonen.tools

import java.nio.file.{Files, Paths}
import java.nio.charset.StandardCharsets
import scala.jdk.CollectionConverters._
import scala.util.matching.Regex

object GwtDocGenerator {

  private val featureRx: Regex = """Feature\("([^"]+)"""".r
  private val scenarioRx: Regex = """Scenario\("([^"]+)"""".r
  private val stepRx: Regex = """\b(Given|When|Then|And)\("([^"]+)"""".r
  private val packageRx: Regex = """(?m)^package\s+([a-zA-Z0-9._]+)""".r

  def main(args: Array[String]): Unit = {
    val testRoot = Paths.get("src", "test", "scala")

    val specFiles =
      Files
        .walk(testRoot)
        .iterator()
        .asScala
        .filter(p => p.toString.endsWith("Spec.scala"))
        .toList

    case class Step(kind: String, text: String)
    case class ScenarioDoc(name: String, steps: List[Step])
    case class FeatureDoc(name: String, scenarios: List[ScenarioDoc])
    case class SuiteDoc(name: String, packageName: String, features: List[FeatureDoc])

    val suites = specFiles.map { path =>
      val content = Files.readString(path)
      val fileName = path.getFileName.toString.stripSuffix(".scala")
      val packageName =
        packageRx.findFirstMatchIn(content).map(_.group(1)).getOrElse("(no package)")

      val featureMatches = featureRx.findAllMatchIn(content).toList

      val features = featureMatches.zipWithIndex.map { case (fMatch, fIdx) =>
        val featureName = fMatch.group(1)
        val featureStart = fMatch.start
        val featureEnd = featureMatches.lift(fIdx + 1).map(_.start).getOrElse(content.length)

        val featureBlock = content.substring(featureStart, featureEnd)

        val scenarioMatches = scenarioRx.findAllMatchIn(featureBlock).toList

        val scenarios = scenarioMatches.zipWithIndex.map { case (sMatch, sIdx) =>
          val scenarioName = sMatch.group(1)
          val scenarioStart = sMatch.start
          val scenarioEnd =
            scenarioMatches.lift(sIdx + 1).map(_.start).getOrElse(featureBlock.length)

          val scenarioBlock = featureBlock.substring(scenarioStart, scenarioEnd)

          val steps =
            stepRx
              .findAllMatchIn(scenarioBlock)
              .map(m => Step(m.group(1), m.group(2)))
              .toList

          ScenarioDoc(scenarioName, steps)
        }

        FeatureDoc(featureName, scenarios)
      }

      SuiteDoc(fileName, packageName, features)
    }

    val sb = new StringBuilder
    sb.append("# GWT Specs\n\n")

    suites.foreach { suite =>
      if (suite.features.nonEmpty) {
        sb.append(s"## ${suite.packageName}.${suite.name}\n\n")
        suite.features.foreach { feature =>
          sb.append(s"### Feature: ${feature.name}\n\n")
          feature.scenarios.foreach { scenario =>
            sb.append(s"#### Scenario: ${scenario.name}\n")

            // Group steps so that Ands are nested under the previous non-And
            case class StepGroup(kind: String, text: String, ands: List[String])

            val grouped: List[StepGroup] = {
              val buf = scala.collection.mutable.ListBuffer.empty[StepGroup]
              var current: Option[StepGroup] = None

              scenario.steps.foreach {
                case Step("And", text) =>
                  current match {
                    case Some(g) =>
                      current = Some(g.copy(ands = g.ands :+ text))
                    case None =>
                      // And without a previous step: treat as its own top-level group
                      current = Some(StepGroup("And", text, Nil))
                  }
                case Step(kind, text) =>
                  // flush previous
                  current.foreach(buf += _)
                  current = Some(StepGroup(kind, text, Nil))
              }
              current.foreach(buf += _)
              buf.toList
            }

            // Render with indentation:
            // - Given ...
            //   - And ...
            // - When ...
            // - Then ...
            grouped.foreach { g =>
              sb.append(s"- ${g.kind} ${g.text}\n")
              g.ands.foreach { andText =>
                sb.append(s"  - And $andText\n")
              }
            }

            sb.append("\n")
          }
        }
      }
    }

    val outDir = Paths.get("target", "gwt-docs")
    if (!Files.exists(outDir)) Files.createDirectories(outDir)
    val outFile = outDir.resolve("gwt-specs-static.md")
    Files.write(outFile, sb.toString().getBytes(StandardCharsets.UTF_8))

    println(s"[GwtDocGenerator] Wrote GWT docs to: ${outFile.toAbsolutePath}")
  }
}
