import io.nats.client.JetStreamStatusException
import io.nats.client.Nats
import io.nats.client.Options
import io.nats.service.Discovery
import io.nats.service.Group
import io.nats.service.Service
import io.nats.service.ServiceEndpoint
import kotlinx.coroutines.future.await
import kotlin.system.exitProcess







suspend fun main(args: Array<String>) {
    val conn = Nats.connect(Options.builder().reportNoResponders().build())


    // Define a service endpoint under the subject 'acme.echo'
    val echoServiceEndpoint = ServiceEndpoint
        .builder()
        .endpointName("Echo")
        .endpointSubject("echo")
        .group(Group("acme"))
        .handler { msg ->
            msg.respond(Nats.connect(),"Echo: ${msg.data.decodeToString()}")
        }
        .build()


    // Add the endpoint to an EchoService
    val echoService = Service
        .builder()
        .connection(conn)
        .name("EchoService")
        .version("1.0.0")
        .addServiceEndpoint(echoServiceEndpoint)
        .build()


    // Start the service
    echoService.startService()


    // Do service discovery
    val discoveredInfo = Discovery(conn, 500, 0).info("EchoService")
    val discoveredSubject = discoveredInfo.firstOrNull()?.endpoints?.firstOrNull()?.subject ?: error("Discovery failed")

    // If we ask the 'ServiceEndpoint' itself, it tells is the current subject
    assertEqual("ServiceEndpoint reported subject\t", "acme.echo", echoServiceEndpoint.subject)

    // If we ask the 'Service' directly via the JVM, it reports the wrong subject
    assertEqual("Service Local Reported subject  \t", "acme.echo", echoService.infoResponse.endpoints.first().subject)

    // If we do service discovery via the API, it reports the wrong subject
    assertEqual("Discover Remote Service Name    \t", "acme.echo", discoveredSubject)


    // If we make a request to the 'acme.echo' subject, the responder handles it correctly
    try {
        val resp = conn.request("acme.echo", "Hello".encodeToByteArray()).await()
        if( resp.data.decodeToString() == "Echo: Hello"){
            println("${tickSymbol} Service responded on correct 'acme.echo' subject")
        } else {
            println("${crossSymbol} Incorrect response")
        }

    } catch (e: JetStreamStatusException){
        if( e.status.isNoResponders ){
            println("${crossSymbol}: No responser")
        } else {
            e.printStackTrace()
        }
    }

    exitProcess(0)


}


