package scala.meta.internal.pc

object xxx:
  extension (sbd: String)
    def foo = sbd + sbd
    def bar = sbd + sbd + sbd
  end extension
