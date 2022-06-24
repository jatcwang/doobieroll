package doobierolltest

import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.TimeUnit
import scala.concurrent.ExecutionContext
import cats.data.{NonEmptyList, NonEmptyVector}
import cats.effect.{IO, Resource}
import cats.effect.unsafe.IORuntime
import cats.kernel.Eq
import cats.syntax.traverse._
import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import doobie.hikari.HikariTransactor
import doobie.implicits.WeakAsyncConnectionIO
import doobie.syntax.connectionio._
import doobie.syntax.stream._
import doobie.util.{Get, Read}
import doobie.util.query.Query0
import doobierolltest.TestDataInstances.Infallible
import doobierolltest.model._
import fs2.Stream
import io.circe.Decoder
import org.openjdk.jmh.annotations._
import shapeless.::
import shapeless.HNil
import skunk.Session

// docker run --rm -p 5432:5432 -e POSTGRES_PASSWORD=password -e POSTGRES_DB=doobieroll -v "$(pwd)/modules/bench/src/main/resources/sql/tables.sql:/docker-entrypoint-initdb.d/01-tables.sql" -v "$(pwd)/modules/bench/src/main/resources/sql/data.sql:/docker-entrypoint-initdb.d/02-data.sql" postgres:12

object SQLComparisonBench {
  import doobie.postgres.implicits.UuidType

  private implicit val eqDbCompany: Eq[DbCompany] = Eq.fromUniversalEquals
  private implicit val eqDbDepartment: Eq[DbDepartment] = Eq.fromUniversalEquals

  private implicit val decoderEmployee: Decoder[Employee] = io.circe.generic.semiauto.deriveDecoder
  private implicit val decoderDepartment: Decoder[Department] =
    io.circe.generic.semiauto.deriveDecoder
  private implicit val decoderCompany: Decoder[Company] = io.circe.generic.semiauto.deriveDecoder

  private val sql = """
    SELECT
      company_id,
      company.name,

      department_id,
      company_id,
      department.name,

      employee_id,
      department_id,
      employee.name
    FROM company
    JOIN department USING (company_id)
    JOIN employee USING (department_id)
  """
  private val query = Query0[DbCompany :: DbDepartment :: DbEmployee :: HNil](sql)
  private val queryOrdered = Query0[DbCompany :: DbDepartment :: DbEmployee :: HNil](
    sql concat " ORDER BY company_id, department_id, employee_id",
  )

  private val sqlCompany = {
    import doobie.syntax.string._
    fr"""
      SELECT
        company_id,
        company.name
      FROM company
    """.query[DbCompany]
  }
  private def sqlDepartment(companyIds: NonEmptyVector[UUID]) = {
    import doobie.syntax.string._

    fr"""
      SELECT
        department_id,
        company_id,
        department.name
      FROM department
      WHERE ${doobie.util.fragments.in(fr"company_id", companyIds)}
    """.query[DbDepartment]
  }
  private def sqlEmployee(departmentIds: NonEmptyVector[UUID]) = {
    import doobie.syntax.string._

    fr"""
      SELECT
        employee_id,
        department_id,
        employee.name
      FROM employee
      WHERE ${doobie.util.fragments.in(fr"department_id", departmentIds)}
    """.query[DbEmployee]
  }

  private val querySkunk = {
    import skunk.codec.all._
    import skunk.syntax.stringcontext._

    val decoderDbCompany = (uuid ~ text).gimap[DbCompany]
    val decoderDbDepartment = (uuid ~ uuid ~ text).gimap[DbDepartment]
    val decoderDbEmployee = (uuid ~ uuid ~ text).gimap[DbEmployee]
    val decoder = (decoderDbCompany ~ decoderDbDepartment ~ decoderDbEmployee).map {
      case ((c, d), e) =>
        c :: d :: e :: HNil
    }

    val fragment: skunk.Fragment[skunk.Void] = sql"""
      SELECT
        company_id,
        company.name,

        department_id,
        company_id,
        department.name,

        employee_id,
        department_id,
        employee.name
      FROM company
      JOIN department USING (company_id)
      JOIN employee USING (department_id)
    """
    fragment.query(decoder)
  }

  private val sqlJSON = """
    SELECT
      jsonb_build_object(
        'id', company_id,
        'name', company.name,
        'departments', (
          SELECT json_agg(jsonb_build_object(
            'id', department_id,
            'name', department.name,
            'employees', (
              SELECT json_agg(jsonb_build_object(
                'id', employee_id,
                'name', employee.name
              ))
              FROM employee
              WHERE employee.department_id = department.department_id
            )
          ))
          FROM department
          WHERE department.company_id = company.company_id
        )
      )
    FROM company
  """
  private val queryJSON = {
    import doobie.postgres.circe.jsonb.implicits._
    val read = Read.fromGet(pgDecoderGetT[Company])
    Query0(sqlJSON)(read)
  }
  private val queryJSON_jsoniter = {
    import com.github.plokhotnyuk.jsoniter_scala.macros._
    import com.github.plokhotnyuk.jsoniter_scala.core._

    val codec: JsonValueCodec[Company] = JsonCodecMaker.make

    val get = Get.Advanced
      .other[org.postgresql.util.PGobject](
        NonEmptyList.of("jsonb"),
      )
      .map { o =>
        val bytes = o.getValue.getBytes(StandardCharsets.UTF_8)
        readFromArray(bytes)(codec)
      }
    val read = Read.fromGet(get)
    Query0(sqlJSON)(read)
  }
}

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.SECONDS)
@BenchmarkMode(Array(Mode.Throughput))
class SQLComparisonBench {
  import SQLComparisonBench._

  implicit val ioRuntime: IORuntime = IORuntime.global

  private val port = 5432
  private val database = "doobieroll"
  private val user = "postgres"
  private val pass = "password"

  private val (transactor, _) = {
    val ec = ExecutionContext.global

    for {
      dataSource <- Resource.fromAutoCloseable(IO {
        val hikariConfig = new HikariConfig()
        hikariConfig.setDriverClassName("org.postgresql.Driver")
        hikariConfig.setJdbcUrl(s"jdbc:postgresql://localhost:$port/$database")
        hikariConfig.setUsername(user)
        hikariConfig.setPassword(pass)
        new HikariDataSource(hikariConfig)
      })
    } yield HikariTransactor[IO](
      dataSource,
      ec,
    )
  }.allocated.unsafeRunSync()

  private val (session, _) = {
    import natchez.Trace.Implicits.noop

    Session.pooled[IO](
      host = "localhost",
      port = port,
      database = database,
      user = user,
      password = Some(pass),
      max = 5,
    )
  }.allocated.unsafeRunSync()

  @Benchmark
  def naive: Iterable[Company] = {
    val conn = query.to[Vector].map { results =>
      Naive.assembleUngrouped(results)
    }
    conn.transact(transactor).unsafeRunSync()
  }

  @Benchmark
  def roll: Vector[Company] = {
    val conn = query.to[Vector].map { results =>
      Infallible.companyAssembler.assemble(results)
    }
    conn.transact(transactor).unsafeRunSync()
  }

  @Benchmark
  def fs2: Vector[Company] = {
    val stream = queryOrdered.stream
      .transact(transactor)
      .groupAdjacentBy(_.head)
      .map { case (company, chunk) =>
        val departments = Stream
          .chunk(chunk)
          .groupAdjacentBy(_.tail.head)
          .map { case (department, chunk) =>
            val employees = chunk.map(t => Employee.fromDb(t.tail.tail.head))
            Department.fromDb(department, employees.toVector)
          }
          .compile
          .toVector
        Company.fromDb(company, departments)
      }
    stream.compile.toVector.unsafeRunSync()
  }

  @Benchmark
  def jsonCirce: Vector[Company] = {
    val conn = queryJSON.to[Vector]
    conn.transact(transactor).unsafeRunSync()
  }

  @Benchmark
  def jsonJsoniter: Vector[Company] = {
    val conn = queryJSON_jsoniter.to[Vector]
    conn.transact(transactor).unsafeRunSync()
  }

  @Benchmark
  def multipleQueries: Vector[Company] = {
    val conn = for {
      companies <- sqlCompany.to[Vector]
      companyIds = NonEmptyVector.fromVector(companies.map(_.id))
      departments <- companyIds.traverse(sqlDepartment(_).to[Vector]).map(_.getOrElse(Vector.empty))
      departmentsByCompany = departments.groupBy(_.companyId)
      departmentIds = NonEmptyVector.fromVector(departments.map(_.id))
      employees <- departmentIds.traverse(sqlEmployee(_).to[Vector]).map(_.getOrElse(Vector.empty))
      employeesByDepartment = employees.groupBy(_.departmentId)
    } yield companies.map { c =>
      val departments = {
        val dbDepartments = departmentsByCompany.getOrElse(c.id, Vector.empty)
        dbDepartments.map { d =>
          val dbEmployees = employeesByDepartment.getOrElse(d.id, Vector.empty)
          val employees = dbEmployees.map(Employee.fromDb)
          Department.fromDb(d, employees)
        }
      }
      Company.fromDb(c, departments)
    }
    conn.transact(transactor).unsafeRunSync()
  }

  @Benchmark
  def skunkNaive: Iterable[Company] =
    session
      .use { s =>
        s.execute(querySkunk)
      }
      .map { results =>
        Naive.assembleUngrouped(results.toVector)
      }
      .unsafeRunSync()

}
