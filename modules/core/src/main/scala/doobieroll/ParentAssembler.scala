package doobieroll

import shapeless.{HList, ::, HNil}
import doobieroll.impl.{ParentVisitor, Accum, OptParentVisitor}

import scala.annotation.nowarn
import scala.collection.mutable
import scala.collection.compat.IterableOnce

trait ParentAssembler[F[_], A, Dbs <: HList] extends Assembler[F, A, Dbs] {
  self =>
  private[doobieroll] override def makeVisitor(
    accum: Accum,
    idx: Int,
  ): ParentVisitor[F, A, Dbs]

  def assemble(rows: IterableOnce[Dbs]): Vector[F[A]] = {
    import doobieroll.ParentAssembler.hlistToArraySeq

    @nowarn
    val it = rows.toIterator
    if (it.isEmpty) return Vector.empty
    val accum = new Accum()

    val parVis = this.makeVisitor(accum, 0)

    it.foreach { dbs =>
      parVis.recordTopLevel(hlistToArraySeq(dbs))
    }

    parVis.assembleTopLevel()
  }

  final override def optional[ADb, RestDb <: HList](
    implicit ev: (ADb :: RestDb) =:= Dbs,
  ): ParentAssembler[F, A, Option[ADb] :: RestDb] = {
    new ParentAssembler[F, A, Option[ADb] :: RestDb] {
      private[doobieroll] override def makeVisitor(
        accum: Accum,
        idx: Int,
      ): ParentVisitor[F, A, Option[ADb] :: RestDb] =
        OptParentVisitor.fromAssembler(
          self.asInstanceOf[ParentAssembler[F, A, ADb :: RestDb]],
          accum,
          idx,
        )
    }
  }
}

object ParentAssembler {

  private[ParentAssembler] def hlistToArraySeq[Dbs <: HList](
    h: Dbs,
  ): Vector[Any] = {
    val arr = mutable.ArrayBuffer.empty[Any]

    @scala.annotation.tailrec
    def impl(h: HList): Unit = {
      h match {
        case x :: r => {
          arr += x
          impl(r)
        }
        case HNil => ()
      }
    }

    impl(h)
    arr.toVector
  }

}
