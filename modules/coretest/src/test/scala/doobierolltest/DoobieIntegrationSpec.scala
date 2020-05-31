package doobierolltest

import java.util.concurrent.Executors

import cats.effect.Blocker
import com.github.dockerjava.core.DefaultDockerClientConfig
import com.whisk.docker.DockerReadyChecker.LogLineContains
import com.whisk.docker.{DockerContainer, DockerContainerManager}
import com.whisk.docker.impl.dockerjava.{Docker, DockerJavaExecutorFactory}
import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import zio._
import zio.test.environment.TestEnvironment
import zio.test._
import zio.test.Assertion._
import zio.interop.catz._

import scala.concurrent.ExecutionContext
import doobie.Transactor
import doobie.free.connection.ConnectionIO
import doobie.implicits._
import Db._

import scala.concurrent.duration._

object DoobieIntegrationSpec extends DefaultRunnableSpec {

  override def spec: ZSpec[TestEnvironment, Nothing] =
    suite("DoobieIntegrationSpec")(
      testM("df") {
        runSql(fr"SELECT 1".query[Int].unique)
          .map(result => assert(result)(equalTo(1)))
          .orDie
      },
      testM("another") {
        runSql(fr"SELECT 2".query[Int].unique)
          .map(result => assert(result)(equalTo(2)))
          .orDie
      },
    ).provideSomeLayerShared(postgresContainerLayer >>> fromTransactor)

  def containerLayer(
    containers: List[DockerContainer],
  ): ZLayer[Any, Nothing, Has[Transactor[Task]]] = {
    val steps = for {
      executorService <- ZManaged
        .make(
          ZIO.succeed {
            ExecutionContext
              .fromExecutorService(
                Executors.newFixedThreadPool(Math.max(1, containers.length * 2)),
              ),
          },
        )(e => ZIO.succeed(e.shutdown()))
      dbExecutorService <- ZManaged
        .make(
          ZIO.succeed {
            ExecutionContext
              .fromExecutorService(
                Executors.newFixedThreadPool(2),
              ),
          },
        )(e => ZIO.succeed(e.shutdown()))
      manager = {
        val docker = new DockerJavaExecutorFactory(
          new Docker(DefaultDockerClientConfig.createDefaultConfigBuilder().build()),
        )
        new DockerContainerManager(containers, docker.createExecutor())(executorService)
      }
      _ <- ZManaged.make(ZIO.fromFuture { _ =>
        manager.initReadyAll(2.minutes)
      }.orDie)(_ =>
        ZIO.fromFuture { _ =>
          manager.stopRmAll()
        }.orDie,
      )
      transactor <- ZManaged
        .fromAutoCloseable(ZIO.succeed {
          Thread.sleep(500)
          val hikariConfig = new HikariConfig()
          hikariConfig.setDriverClassName("org.postgresql.Driver")
          hikariConfig.setJdbcUrl("jdbc:postgresql://localhost:5432/postgres")
          hikariConfig.setUsername("postgres")
          hikariConfig.setPassword("postgres")
          hikariConfig.setMaximumPoolSize(4)
          hikariConfig.setConnectionTimeout(1000)
          new HikariDataSource(hikariConfig)
        })
        .map(datasource =>
          Transactor
            .fromDataSource[Task]
            .apply(datasource, dbExecutorService, Blocker.liftExecutionContext(dbExecutorService)): Transactor[
            Task,
          ],
        )
        .asService
    } yield transactor

    ZLayer(steps)
  }

  val postgresContainerLayer = containerLayer(
    List(
      DockerContainer("postgres:10.5")
        .withEnv("POSTGRES_PASSWORD=postgres")
        .withPorts((5432, Some(5432)))
        .withReadyChecker(
          LogLineContains("database system is ready to accept connections"),
        ),
    ),
  )

}

