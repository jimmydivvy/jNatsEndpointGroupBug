# JNats - ServiceEndpoint 'group' bug


This is a sample case to demonstrate a bug in JNats where the ServiceEndpoint 'group' parameter
is not included in the subject name when generating the service 'info' response.

The underlying cause appears to be here:

https://github.com/nats-io/nats.java/blame/2.17.0/src/main/java/io/nats/service/Service.java#L81

Specifically
- This line adds 'ServiceEndpoint.endpoint' to the 'InfoResponse' object
- However the 'Endpoint' object does not have a 'group' field, so it is not included in the response
- The 'ServiceEndpoint' object itself does have a 'group' field, and ServiceEndpoint.getSubject() works correctly

This means that
 - the info response is unaware of the 'group', and does not report it
 - the service itself does listen on the correct subject

 To Test

 - Run nats-server on localhost:4222
 - ./gradlew run

Example output:

```shell
✅ ServiceEndpoint reported subject      : acme.echo
❌ Service Local Reported subject        : echo (Expected: acme.echo)
❌ Discover Remote Service Name          : echo (Expected: acme.echo)
✅ Service responded on correct 'acme.echo' subject
```