package example

import example.model.Company

import scala.collection.mutable

object Func {

  implicit class DbConvOps[DT](val dt: DT) extends AnyVal {
    def getId[Id](implicit dbConv: DbConv[_, DT, Id]): Id = dbConv.getId(dt)

    def mkNoChildren[T](implicit dbConv: DbConv[T, DT, _]): T =
      dbConv.mkNoChild(dt)
  }

  implicit class AddChildOps[T](val t: T) extends AnyVal {
    def withChild[C](children: List[C])(implicit addChild: AddChild[T, C]): T =
      addChild.withChildren(t, children)
  }

  trait DbChild[ParId, DC] {
    def parentId(dc: DC): ParId
  }

  trait DbConv[T, DT, Id] {
    def mkNoChild(dt: DT): T
    def getId(dt: DT): Id
  }

  trait AddChild[T, C] {
    def withChildren(t: T, c: List[C]): T
  }

  def assembleOrdered[T1, T2, T3, Dt1, Dt2, Dt3, Id1, Id2, Id3](
    rows: List[Tuple3[Dt1, Dt2, Dt3]]
  )(
    implicit
    dbConv1: DbConv[T1, Dt1, Id1],
    dbConv2: DbConv[T2, Dt2, Id2],
    dbConv3: DbConv[T3, Dt3, Id3],
    addChild12: AddChild[T1, T2],
    addChild23: AddChild[T2, T3],
  ): List[T1] = {

    if (rows.isEmpty)
      return List.empty

    var lastId1: Id1 = null.asInstanceOf[Id1]
    var lastId2: Id2 = null.asInstanceOf[Id2]

    var lastDt1: Dt1 = null.asInstanceOf[Dt1]
    var lastDt2: Dt2 = null.asInstanceOf[Dt2]

    val accumT1s = mutable.ArrayBuffer.empty[T1]
    val accumT2s = mutable.ArrayBuffer.empty[T2]
    val accumT3s = mutable.ArrayBuffer.empty[T3]

    rows.foreach { case (dt1, dt2, dt3) =>

      val curId1 = dt1.getId
      val curId2 = dt2.getId

      def setThings() = {
        lastId1 = curId1
        lastId2 = curId2

        lastDt1 = dt1
        lastDt2 = dt2
      }

      if (lastId1 == null) {
        setThings()
      }

      if (dt1.getId != lastId1) {
        val newT2 = lastDt2.mkNoChildren.withChild(accumT3s.toList)
        val allT2s = (accumT2s :+ newT2).toList
        accumT1s += lastDt1.mkNoChildren.withChild(allT2s)

        accumT2s.clear()
        accumT3s.clear()
      }
      else if (dt2.getId != lastId2) {
        val newT2 = lastDt2.mkNoChildren.withChild(accumT3s.toList)

        accumT2s += newT2

        accumT3s.clear()
      }

      accumT3s += dt3.mkNoChildren

      setThings()
    }

    // Final wrap up

    val thisT2 = lastDt2.mkNoChildren.withChild(accumT3s.toList)
    val restT2s = accumT2s :+ thisT2
    val thisT1 = lastDt1.mkNoChildren.withChild(restT2s.toList)

    accumT1s += thisT1

    accumT1s.toList
  }

  def assembleUnordered[T1, T2, T3, Dt1, Dt2, Dt3, Id1, Id2, Id3](
    rows: List[Tuple3[Dt1, Dt2, Dt3]],
  )(
    implicit
    dbConv1: DbConv[T1, Dt1, Id1],
    dbConv2: DbConv[T2, Dt2, Id2],
    dbConv3: DbConv[T3, Dt3, Id3],
    addChild12: AddChild[T1, T2],
    addChild23: AddChild[T2, T3],
  ): List[T1] = {
    val t1s = mutable.Map.empty[Id1, T1]
    val t2s = mutable.Map.empty[Id2, T2]
    val t3s = mutable.Map.empty[Id3, T3] // T3 has no child

    // FIXME: no need multidict
    val lookup12 = mutable.MultiDict.empty[Id1, Id2]
    val lookup23 = mutable.MultiDict.empty[Id2, Id3]

    rows.foreach {
      case (dt1, dt2, dt3) =>
        val t1Id = dt1.getId

        if (!t1s.contains(t1Id)) {
          t1s += t1Id -> dt1.mkNoChildren
        }

        val t2Id = dt2.getId

        if (!t2s.contains(t2Id)) {
          t2s += t2Id -> dt2.mkNoChildren
          lookup12.addOne(t1Id -> t2Id)
        }

        val t3Id = dt3.getId

        if (!t3s.contains(t3Id)) {
          t3s += t3Id -> dt3.mkNoChildren
          lookup23.addOne(t2Id -> t3Id)
        }

    }

    t2s.mapValuesInPlace {
      case (id, t2) =>
        val children = lookup23.get(id).map { t3s(_) }.toList
        t2.withChild(children)
    }

    t1s
      .mapValuesInPlace {
        case (id, t1) =>
          val children = lookup12.get(id).map(t2s(_)).toList
          t1.withChild(children)
      }
      .values
      .toList

  }

  def normalizeCompanies(companies: List[Company]): List[Company] = {
    companies.map { c =>
      c.copy(
        departments = c.departments.map { d =>
          d.copy(
            employees = d.employees.sortBy(_.id)
          )
        }.sortBy(_.id)
      )
    }.sortBy(_.id)
  }

}














