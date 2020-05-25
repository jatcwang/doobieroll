package orutest

import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

import orutest.TestModelHelpers._
import orutest.model.{DbCompany, DbDepartment, DbEmployee, Wrapper}
import io.circe.parser._
import org.openjdk.jmh.annotations._
import org.openjdk.jmh.infra.Blackhole
import oru.{Naive, UngroupedAssembler}
import shapeless._
import TestDataInstances._

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.SECONDS)
@BenchmarkMode(Array(Mode.Throughput))
class AssembleBench {

  val wrappers10K: Vector[Wrapper] = {
    val r = new String(
      this.getClass.getClassLoader.getResourceAsStream("testdata.json").readAllBytes(),
      StandardCharsets.UTF_8
    )
    decode[Vector[Wrapper]](r)
      .getOrElse(throw new Exception("decode failed"))
  }

  val hlist10K: Vector[DbCompany :: DbDepartment :: DbEmployee :: HNil] = wrappers10K.map(_.asHList)
  val optHList10k: Vector[DbCompany :: Option[DbDepartment] :: Option[DbEmployee] :: HNil] =
    wrappers10K.map(wrapperToOptHList)

  val hlist1K: Vector[DbCompany :: DbDepartment :: DbEmployee :: HNil] =
    wrappers10K.map(_.asHList).take(1000)
  val optHList1k: Vector[DbCompany :: Option[DbDepartment] :: Option[DbEmployee] :: HNil] =
    optHList10k.take(1000)

  val hlist100: Vector[DbCompany :: DbDepartment :: DbEmployee :: HNil] =
    wrappers10K.map(_.asHList).take(100)

  @Benchmark
  def ungrouped10k(blackhole: Blackhole): Unit =
    blackhole.consume(UngroupedAssembler.assembleUngrouped(companyAssembler)(hlist10K))

//  @Benchmark
//  def ungroupedOpt10k(blackhole: Blackhole): Unit =
//    blackhole.consume(UngroupedAssembler.assembleUngrouped(companyOptAssembler)(optHList10k))

  @Benchmark
  def ungrouped1k(blackhole: Blackhole): Unit =
    blackhole.consume(UngroupedAssembler.assembleUngrouped(companyAssembler)(hlist1K))

  @Benchmark
  def ungrouped100(blackhole: Blackhole): Unit =
    blackhole.consume(UngroupedAssembler.assembleUngrouped(companyAssembler)(hlist100))

//  @Benchmark
//  def ungroupedOpt1k(blackhole: Blackhole): Unit =
//    blackhole.consume(UngroupedAssembler.assembleUngrouped(companyOptAssembler)(optHList1k))

//  @Benchmark
//  def naive10k(blackhole: Blackhole): Unit =
//    blackhole.consume(Naive.assembleUngrouped(hlist10K))

}
