import io.nats.client.JetStreamStatusException
import io.nats.client.Nats
import io.nats.client.Options
import io.nats.service.Discovery
import io.nats.service.Group
import io.nats.service.Service
import io.nats.service.ServiceEndpoint
import kotlinx.coroutines.future.await


/**
 * This is a sample case to demonstrate a bug in JNats where the ServiceEndpoint 'group' parameter
 * is not included in the subject name when generating the service 'info' response.
 *
 * The underlying cause appears to be here:
 *
 * https://github.com/nats-io/nats.java/blame/2.17.0/src/main/java/io/nats/service/Service.java#L81
 *
 * Specifically
 *   - This line adds 'ServiceEndpoint.endpoint' to the 'InfoResponse' object
 *   - However the 'Endpoint' object does not have a 'group' field, so it is not included in the response
 *   - The 'ServiceEndpoint' object itself does have a 'group' field, and ServiceEndpoint.getSubject() works correctly
 *
 * This means that
 * - the info response is unaware of the 'group', and does not report it
 * - the service itself does listen on the correct subject
 *
 * To Test
 *
 * - Run nats-server on localhost:4222
 * - Run this code
 *
 */




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


}


