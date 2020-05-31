package doobierolltest

import zio.{Has, URLayer, ZLayer, ZIO, Task}
import doobie.{Transactor, ConnectionIO}
import doobie.implicits._
import zio.interop.catz._

object Db {

  type Dep = Has[Service]

  class Service(tran: Transactor[Task]) {
    def runSql[A](conn: ConnectionIO[A]): ZIO[Any, Throwable, A] =
      conn.transact(tran)
  }

  def runSql[A](conn: ConnectionIO[A]): ZIO[Dep, Throwable, A] =
    ZIO.accessM(ser => ser.get.runSql(conn))

  val fromTransactor: URLayer[Has[Transactor[Task]], Has[Service]] =
    ZLayer.fromFunction(tran => new Service(tran.get))

}
