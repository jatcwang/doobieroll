package example

object Better {

  trait DbAtom[T, Dt, Id] {
    def id(dt: Dt): Id
    def convert(dt: Dt): T
  }

  trait DbPartial1[T, Dt, Id, C] {
    def id(dt: Dt): Id
    def diff(prev: Dt, curr: Dt): Boolean

    def make(dt: Dt, c1s: Vector[C]): T
  }

  trait Blah[T, Dt]


}
