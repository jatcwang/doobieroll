package doobierolltest

import zio.{Has, Task, UIO, ZIO}
import doobie._
import doobie.implicits._
import zio.interop.catz._

package object db {
  type Db = Has[Db.Service]

  object Db {
    class Service(transactor: Transactor[Task]) {
      def runSql[A](conn: ConnectionIO[A]): UIO[A] =
        conn.transact(transactor).orDie
    }
  }

  def runSql[A](conn: ConnectionIO[A]): ZIO[Db, Nothing, A] =
    ZIO.accessM(_.get.runSql(conn))
}
