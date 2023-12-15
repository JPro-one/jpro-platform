package one.jpro.platform.routing

import simplefx.experimental._

import java.util.function.Predicate


object Route {
  def empty(): Route = (r) => null
}
@FunctionalInterface
trait Route {
  def apply(r: Request): FXFuture[Response]

  def and(x: Route): Route = { request =>
    val r = apply(request)
    if(r == null) {
      x.apply(request)
    } else {
      r.flatMap{ r =>
        if(r == null) {
          val r = x.apply(request)
          if(r == null) {
            FXFuture.unit(null)
          } else {
            r
          }
        } else FXFuture.unit(r)
      }
    }
  }
  def domain(domain: String, route: Route): Route = and((r: Request) => {
    if(r.domain == domain) {
      route.apply(r)
    } else {
      null
    }
  })
  def path(path: String, route: Route): Route = and((r: Request) => {
    if(r.path.startsWith(path + "/")) {
      val r2 = r.copy(path = r.path.drop(path.length), directory = r.resolve(path))
      route.apply(r2)
    } else {
      null
    }
  })
  def filter(filter: Filter): Route = filter(this)
  def filterWhen(cond: Predicate[Request], filter: (Request) => Filter): Route = { r =>
    if(cond.test(r)) {
      filter.apply(r)(this).apply(r)
    } else {
      this.apply(r)
    }
  }
  def filterWhenFuture(cond: Predicate[Request], filter: (Request) => FXFuture[Filter]): Route = { r =>
    if(cond.test(r)) {
      filter(r).flatMap(filter => filter(this).apply(r))
    } else {
      this.apply(r)
    }
  }
  def when(cond: Predicate[Request], _then: Route): Route = and(r => {
    val condResult = cond.test(r)
    val r2: FXFuture[Response] = if(condResult) _then(r) else null
    r2
  })
  def when(cond: Predicate[Request], _then: Route, _else: Route): Route = and(r => {
    if(cond.test(r)) _then(r) else _else(r)
  })

  def whenFuture(cond: java.util.function.Function[Request, FXFuture[java.lang.Boolean]], _then: Route): Route = and(r => {
    cond.apply(r).flatMap(condResult => if (condResult) _then(r) else null)
  })

  def whenFuture(cond: java.util.function.Function[Request, FXFuture[java.lang.Boolean]], _then: Route, _else: Route): Route = and(r => {
    cond.apply(r).flatMap(condResult => if (condResult) _then(r) else _else(r))
  })
}
