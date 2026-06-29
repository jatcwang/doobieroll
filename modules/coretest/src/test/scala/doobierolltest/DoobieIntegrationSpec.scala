package doobierolltest

import java.util.concurrent.Executors
import com.github.dockerjava.core.DefaultDockerClientConfig
import com.whisk.docker.DockerReadyChecker.LogLineContains
import com.whisk.docker.{DockerContainer, DockerContainerManager}
import com.whisk.docker.impl.dockerjava.{Docker, DockerJavaExecutorFactory}
import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import zio.{Task, URIO, ZIO, ZLayer}
import zio.test._
import zio.test.Assertion._
import zio.interop.catz._
import shapeless.{::, HNil}

import scala.concurrent.ExecutionContext
import doobie.implicits._
import doobie.util.update.Update
import doobierolltest.model.{Company, DbCompany, DbDepartment, DbEmployee, DbInvoice}
import TestDataHelpers._
import DoobieRowInstances._
import doobie.hikari.HikariTransactor
import doobie.util.Read
import doobie.util.query.Query0
import doobierolltest.db._

object DoobieIntegrationSpec extends ZIOSpecDefault {
  private val dockerTimeout = scala.concurrent.duration.DurationInt(3).minutes

  override def spec =
    suite("DoobieIntegrationSpec")(
      suite("Assembler")(
        test("roundtrip with small test data (inner join)") {
          val orig = TestData.expectedCompanies
          (for {
            _ <- insertDbData(orig)
            rows <- fetchCompany
          } yield {
            val result = TestDataInstances.Infallible.companyAssembler.assemble(rows)
            assert(normalizeCompanies(result))(equalTo(orig))
          }).ensuring(cleanupTables)
        },
        test("roundtrip with small test data (left join)") {
          val orig = TestData.expectedCompaniesWithSomeEmptyChildren
          (for {
            _ <- insertDbData(orig)
            rows <- fetchCompanyOpt
          } yield {
            val result = TestDataInstances.Infallible.companyOptAssembler.assemble(rows)
            assert(normalizeCompanies(result))(equalTo(orig))
          }).ensuring(cleanupTables)
        },
      ),
    ).provideShared(postgresContainerLayer >>> withTestTables) @@ TestAspect.sequential

  def containerLayer(
    containers: List[DockerContainer],
  ): ZLayer[Any, Nothing, HikariTransactor[Task]] =
    ZLayer.scoped {
      for {
        executorService <-
          ZIO.acquireRelease(
            ZIO.succeed {
              ExecutionContext
                .fromExecutorService(
                  Executors.newFixedThreadPool(Math.max(1, containers.length * 2)),
                )
            },
          )(e => ZIO.succeed(e.shutdown()))
        dbExecutorService <-
          ZIO.acquireRelease(
            ZIO.succeed {
              ExecutionContext
                .fromExecutorService(
                  Executors.newFixedThreadPool(2),
                )
            },
          )(e => ZIO.succeed(e.shutdown()))
        manager = {
          val docker = new DockerJavaExecutorFactory(
            new Docker(DefaultDockerClientConfig.createDefaultConfigBuilder().build()),
          )
          new DockerContainerManager(containers, docker.createExecutor())(executorService)
        }
        _ <- ZIO.acquireRelease(
          ZIO.fromFuture { implicit ec =>
            manager.pullImages(dockerTimeout).flatMap(_ => manager.initReadyAll(dockerTimeout))
          }.orDie,
        )(_ =>
          ZIO.fromFuture { _ =>
            manager.stopRmAll(dockerTimeout)
          }.orDie,
        )
        transactor <-
          ZIO
            .acquireRelease(
              ZIO.succeed {
                Thread.sleep(500)
                val hikariConfig = new HikariConfig()
                hikariConfig.setDriverClassName("org.postgresql.Driver")
                hikariConfig.setJdbcUrl("jdbc:postgresql://localhost:5432/postgres")
                hikariConfig.setUsername("postgres")
                hikariConfig.setPassword("postgres")
                hikariConfig.setMaximumPoolSize(4)
                hikariConfig.setConnectionTimeout(1000)
                new HikariDataSource(hikariConfig)
              },
            )(datasource => ZIO.succeed(datasource.close()))
            .map(datasource =>
              HikariTransactor[Task](
                datasource,
                dbExecutorService,
              ),
            )
      } yield transactor
    }

  val postgresContainerLayer: ZLayer[Any, Nothing, HikariTransactor[Task]] = containerLayer(
    List(
      DockerContainer("postgres:12.3-alpine")
        .withEnv("POSTGRES_PASSWORD=postgres")
        .withPorts((5432, Some(5432)))
        .withReadyChecker(
          LogLineContains("database system is ready to accept connections"),
        ),
    ),
  )

  val withTestTables: ZLayer[HikariTransactor[Task], Nothing, Db] =
    ZLayer.fromZIO {
      for {
        transactor <- ZIO.service[HikariTransactor[Task]]
        service = new Db.Service(transactor)
        _ <- service.runSql(fr"""
            CREATE TABLE company(
              id UUID PRIMARY KEY,
              name TEXT NOT NULL
            );

            CREATE TABLE department(
              id UUID PRIMARY KEY,
              name TEXT NOT NULL,
              company_id UUID NOT NULL REFERENCES company (id)
            );

            CREATE TABLE employee(
              id UUID PRIMARY KEY,
              name TEXT NOT NULL,
              department_id UUID NOT NULL REFERENCES department (id)
            );

            CREATE TABLE invoice(
              id UUID PRIMARY KEY,
              amount INT NOT NULL
            );
          """.update.run.map(_ => ()))
      } yield service
    }

  def insertDbCompany: Update[DbCompany] = {
    val cols = DbCompany.columns
    Update[DbCompany](
      s"INSERT INTO ${cols.tableNameStr} ${cols.listWithParenStr} VALUES ${cols.parameterizedWithParenStr}",
    )
  }

  def insertDbDepartment: Update[DbDepartment] = {
    val cols = DbDepartment.columns
    Update[DbDepartment](
      s"INSERT INTO ${cols.tableNameStr} ${cols.listWithParenStr} " +
        s"VALUES ${cols.parameterizedWithParenStr}",
    )
  }

  def insertDbEmployee: Update[DbEmployee] = {
    val cols = DbEmployee.columns
    Update[DbEmployee](
      s"INSERT INTO ${cols.tableNameStr} ${cols.listWithParenStr} " +
        s"VALUES ${cols.parameterizedWithParenStr}",
    )
  }

  def insertDbInvoice: Update[DbInvoice] = {
    val cols = DbInvoice.columns
    Update[DbInvoice](
      s"INSERT INTO ${cols.tableNameStr} ${cols.listWithParenStr} " +
        s"VALUES ${cols.parameterizedWithParenStr}",
    )
  }

  def cleanupTables: ZIO[Db, Nothing, Unit] =
    runSql(Update[Unit](s"TRUNCATE company CASCADE").run(())).unit

  def fetchCompany: URIO[Db, Vector[DbCompany :: DbDepartment :: DbEmployee :: HNil]] =
    fetchCompanyImpl[DbCompany :: DbDepartment :: DbEmployee :: HNil]("INNER")

  def fetchCompanyOpt
    : URIO[Db, Vector[DbCompany :: Option[DbDepartment] :: Option[DbEmployee] :: HNil]] =
    fetchCompanyImpl[DbCompany :: Option[DbDepartment] :: Option[DbEmployee] :: HNil]("LEFT")

  private def fetchCompanyImpl[Dbs: Read](joinType: String): URIO[Db, Vector[Dbs]] = {
    import DbCompany.{columns => companyCols}
    import DbDepartment.{columns => departmentCols}
    import DbEmployee.{columns => employeeCols}
    runSql(
      Query0[Dbs](
        s"""SELECT
           ${companyCols.prefixedStr("c")},
           ${departmentCols.prefixedStr("d")},
           ${employeeCols.prefixedStr("e")}
            FROM company as c
         $joinType JOIN department AS d ON c.id = d.company_id
         $joinType JOIN employee AS e ON d.id = e.department_id
       """,
      ).to[Vector],
    )
  }

  private def insertDbData(companies: Seq[Company]): URIO[Db, Unit] = {
    val rows = companiesToDbData(companies)
    val connIo = for {
      _ <- insertDbCompany.updateMany(rows._1)
      _ <- insertDbDepartment.updateMany(rows._2)
      _ <- insertDbEmployee.updateMany(rows._3)
    } yield ()

    runSql(connIo).unit
  }

}
