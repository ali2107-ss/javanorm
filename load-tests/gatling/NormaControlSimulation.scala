package normacontrol

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._

class NormaControlSimulation extends Simulation {

  private val httpProtocol = http
    .baseUrl(System.getProperty("baseUrl", "http://localhost:8080"))
    .acceptHeader("application/json")
    .contentTypeHeader("application/json")

  private val login = exec(
    http("login")
      .post("/api/v1/auth/login")
      .body(StringBody("""{"login":"admin@demo.ru","password":"Admin1234!"}"""))
      .check(status.is(200))
      .check(jsonPath("$.access_token").saveAs("token"))
  )

  private val browse = exec(
    http("documents")
      .get("/api/v1/documents")
      .header("Authorization", "Bearer #{token}")
      .check(status.is(200))
  ).pause(1.second)
    .exec(
      http("stats")
        .get("/api/v1/stats/top-violations?limit=5")
        .header("Authorization", "Bearer #{token}")
        .check(status.in(200, 429))
    )

  setUp(
    scenario("NormaControl basic REST load")
      .exec(login)
      .repeat(5) {
        exec(browse)
      }
      .inject(rampUsers(25).during(30.seconds))
  ).protocols(httpProtocol)
    .assertions(
      global.responseTime.percentile3.lt(2000),
      global.successfulRequests.percent.gt(95)
    )
}
