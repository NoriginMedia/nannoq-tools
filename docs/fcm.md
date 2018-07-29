Table of Contents
=================

   * [1.0 Building the FCM Server](#10-building-the-fcm-server)
      * [1.0.0 RegistrationHandler](#100-registrationhandler)
         * [RegistrationHandler](#registrationhandler)
      * [1.0.1 DataMessageHandler](#101-datamessagehandler)
         * [DataMessageHandler](#datamessagehandler)
      * [1.0.2 Constructing the Server](#102-constructing-the-server)
         * [FCM Server](#fcm-server)
   * [1.1 Sending Notifications](#11-sending-notifications)
      * [1.1.0 Sending a Topic](#110-sending-a-topic)
      * [1.1.1 Sending to a Registration ID](#111-sending-to-a-registration-id)
      
# 1.0 Building the FCM Server

## 1.0.0 RegistrationHandler

### RegistrationHandler

This is one of the optional components and it is a simple interface to expose incoming devices to business logic for storage. This logic can use the DeviceGroupManager class to create and manage FCM DeviceGroups.

```java
@Fluent
RegistrationService registerDevice(String appPackageName, String fcmId, JsonObject data);
@Fluent
RegistrationService update(String appPackageName, String fcmId, JsonObject data);
@Fluent
RegistrationService handleDeviceRemoval(String messageId, String registrationId, Handler<AsyncResult<FcmDevice>> resultHandler);
```

## 1.0.1 DataMessageHandler

### DataMessageHandler

This is one of the optional components used by the FcmServer class, and its builder. This interface defines what the server should do upon reception of a message that is not linked to registration, e.g. business logic. It also contains setters for both server and sender, as well as a getter for a RegistrationService.

All messages are handled by a field in the data object called "action". These are the default actions pertaining to registering devices:

```java
switch (action) {
    case REGISTER_DEVICE
        getRegistrationService().registerDevice(msg.getCategory(), gcmId, data);
        
        break;
    case UPDATE_ID:
        getRegistrationService().update(msg.getCategory(), gcmId, data);
          
        break;
    case PONG:           
        logger.info("Device is alive...");    
        setDeviceAlive(data);
         
        break;
    default:
        handleIncomingDataMessage(msg);

        break;
}
```

All business logic will be handled in the overriden handleIncomingDataMessage method.

## 1.0.2 Constructing the Server

### FCM Server

Example of construction for server using data and registrationhandlers:

```java
fcmServer = new FcmServer.FcmServerBuilder()
                .withDataMessageHandler(new DataMessageHandlerImpl(registrationService))
                .withRegistrationService(registrationService)
                .build();
```

If you only want support for receiving upstream messages, you should add a datamessagehandler:

```java
fcmServer = new FcmServer.FcmServerBuilder()
                .withDataMessageHandler(new DataMessageHandlerImpl(registrationService))
                .build();
```

If you only need to send downstream messages and dont care about storing devices or sending upstream messages you can just build it directly. 

```java
fcmServer = new FcmServer.FcmServerBuilder().build();
```

***

It must then be deployed on Vert.x. ALL versions of the FcmServer expect the following values in the config for startup:

```javascript
{
    "basePackageNameFcm" : "<string>",
    "gcmSenderId" : "<string>",
    "gcmApiKey" : "<string>"
}
```

basePackageNameFcm is the root packageName all the apps you want to send messages to are based upon. i.e. 

```javascript
{
    "basePackageNameFcm" : "com.nannoq"
}
```

For information on FCM sender Id and Api Key check the docs: [FCM](https://firebase.google.com/docs/cloud-messaging/concept-options#senderid)


```java
vertx.deployVerticle(fcmServer, new DeploymentOptions().setConfig(config()), deployRes -> {
    if (deployRes.failed()) {
        future.fail(deployRes.cause());
    } else {
        // TODO Do the thing!
    }
});
```

# 1.1 Sending Notifications

This section defines how to send notifications.

This functionality can be easily distributed onto the eventbus, and have a client implementation for building the notifications.

```java
@ProxyGen
@VertxGen
public interface NotificationsService {
    Logger logger = LoggerFactory.getLogger(NotificationsService.class.getSimpleName());

    @Fluent
    NotificationsService sendTopicNotification(JsonObject messageBody, Handler<AsyncResult<Boolean>> resultHandler);

    @Fluent
    NotificationsService sendUserNotification(JsonObject messageBody, Handler<AsyncResult<Boolean>> resultHandler);

    @ProxyClose
    void close();
}
```

This body is the body as defined by [FCM](https://firebase.google.com/docs/cloud-messaging/xmpp-server-ref#notification-payload-support) as payload.

## 1.1.0 Sending a Topic

Sending topic message are done by sending the topic as to, and an object that implements the FcmNotification interface to the send method of FcmServer like this:

```java
fcmServer.send("/someTopic/thisNothing", fcmNotification);
```

## 1.1.1 Sending to a Registration ID

Sending direct messages are done by sending the registrationID (or device group id) as to, and an object that implements the FcmNotification interface to the send method of FcmServer like this:

```java
fcmServer.send(registrationId, fcmNotification);
```