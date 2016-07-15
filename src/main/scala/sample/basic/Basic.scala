package sample.basic

import akka.actor.SupervisorStrategy.{Escalate, Restart}
import akka.actor.{Actor, ActorSystem, OneForOneStrategy, Props}
import akka.routing.RoundRobinPool
import akka.pattern.ask
import akka.util.Timeout

import sample.basic.Supervisor.Ping
import sample.basic.Test1.Message1
import sample.basic.Test2.Message2

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

object Test2{
	def props(system: ActorSystem): Props = Props(classOf[Test2], system)

	case class Message2(s: String)
	class FatalException extends Exception
}

class Test2(system: ActorSystem) extends Actor{
	override def receive: Receive = {
		case Message2(message) => {
			println("Hello from Test2!")
			if (math.random < 0.25) {
				sender() ! s"Got your message ;), it's: $message"
			}else throw new AbsolutelyFatalException()
		}
	}
}

object Test1{
	def props(system: ActorSystem): Props = Props(classOf[Test1], system)

	case class Message1(s: String)
}

class Test1(system: ActorSystem) extends Actor{
	override val supervisorStrategy = {
		OneForOneStrategy() {
			case _: Exception => {
				println("Uh Oh, there's an escalation from Test1")
				Escalate
			}
		}
	}

	override def receive: Receive = {
		case Message1(message) => {
			println("Hello from Test1!")
			system.actorOf(Test2.props(system)).tell(Test2.Message2(message), sender())
		}
	}
}

object Supervisor {
	def props(system: ActorSystem): Props = Props(classOf[Supervisor], system)

	case class Ping(s: String)
}

class Supervisor(system: ActorSystem) extends Actor {
	val restartActorOnFatal = {
		OneForOneStrategy(maxNrOfRetries = 10, withinTimeRange = 10 seconds) {
			case _: Exception => {
				println("Uh Oh, there's a restart from Supervisor")
				Restart
			}
		}
	}

	val router = system.actorOf(Test1.props(system).withRouter(RoundRobinPool(10, supervisorStrategy = restartActorOnFatal)), "router")

	override def receive: Receive = {
		case Ping(message) =>
				println("Hello from Supervisor!")
				sender() ! router.tell(Test1.Message1(message), sender())
	}
}

object Akka extends App {
	val system = ActorSystem()
	val actor = system.actorOf(Supervisor.props(system))

	implicit val timeout: Timeout = Timeout(1 minute)
	(actor ? Supervisor.Ping("Akka is Great!")).mapTo[String].map(s => println(s))
}
