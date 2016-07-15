package sample.basic

import akka.actor.SupervisorStrategy.{Escalate, Restart}
import akka.actor.{Actor, ActorSystem, OneForOneStrategy, Props}
import akka.routing.RoundRobinPool
import akka.pattern.ask
import akka.util.Timeout

import sample.basic.Supervisor.{Followup, Ping}
import sample.basic.Test1.Message1
import sample.basic.Test2.{FatalException, Message2}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

object Test2{
	def props(system: ActorSystem): Props = Props(classOf[Test2], system)

	case class Message2(s: String)
  case object Followup2
	class FatalException extends Exception
}

class Test2(system: ActorSystem) extends Actor{

  var messageContainer = "Default"

  override def receive: Receive = {
		case Message2(message) =>
			println("Hello from Test2!")
      messageContainer = message
      println(s"My message is $messageContainer")
//			if (math.random < 0.25) {
				//sender() ! s"Got your message ;), it's: $message"
			throw new FatalException
    case Test2.Followup2 => println(s"My message is $messageContainer")
  }

}

object Test1{
	def props(system: ActorSystem): Props = Props(classOf[Test1], system)

	case class Message1(s: String)
  case object Followup1
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

  val child = system.actorOf(Test2.props(system))

	override def receive: Receive = {
		case Message1(message) =>
			println("Hello from Test1!")
			child ! Test2.Message2(message)
		case Test1.Followup1 =>
      println("Test 1 following up")
      child ! Test2.Followup2

	}
}

object Supervisor {
	def props(system: ActorSystem): Props = Props(classOf[Supervisor], system)

	case class Ping(s: String)
  case object Followup
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
			  router ! Test1.Message1(message)
				//sender() ! s"Got your message ;), it's: $message"
    case Followup =>
      println("Supervisor following up.")
      router ! Test1.Followup1
	}
}

object Akka extends App {
	val system = ActorSystem()
	val actor = system.actorOf(Supervisor.props(system))

	implicit val timeout: Timeout = Timeout(1 minute)
	(actor ? Supervisor.Ping("Akka is Great!")).mapTo[String].map(s => println(s))
  Thread.sleep(100)
  actor ! Supervisor.Followup
}
