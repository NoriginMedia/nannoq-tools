Table of Contents
=================

   * [1.0 Usage of ServiceManager](#10-usage-of-servicemanager)
      * [ServiceManager](#servicemanager)
            * [Services](#services)
               * [Publish:](#publish)
               * [Consume:](#consume)
               * [UnPublish:](#unpublish)
            * [API's](#apis)
               * [Publish:](#publish-1)
               * [Consume:](#consume-1)
               * [UnPublish:](#unpublish-1)
   * [1.1 Usage of ApiManager](#11-usage-of-apimanager)
      * [ApiManager](#apimanager)
            * [Creating an internal record](#creating-an-internal-record)
            * [Creating an external record](#creating-an-external-record)
            * [Generic request with circuitbreaker support](#generic-request-with-circuitbreaker-support)
            
# 1.0 Usage of ServiceManager

## ServiceManager

After publish services and API's are available for consumption anywhere on the same cluster as the publisher, and locally. The ServiceManager will auto unregister all published and consumed services and APIs on shutdown of vertx.

#### Services

##### Publish:

Publishing is as easy as supplying the interface you wish to publish, and an object of a class that implements that interface. The default name is the getSimpleName() of the type, but you can supply a custom address as well.

```java
ServiceManager.getInstance().publishService(HeartBeatService.class, heartBeatService);
ServiceManager.getInstance().publishService(HeartBeatService.class, "SOME_ADDRESS", heartBeatService);
```
or
```java
ServiceManager.getInstance().publishService(HeartBeatService.class, heartBeatService, resultHandler);
ServiceManager.getInstance().publishService(HeartBeatService.class, "SOME_ADDRESS", heartBeatService, resultHandler);
```
##### Consume:

Consuming is preformed easily by supplying the Interface, this will deliver a service object asynchronously.

```java
ServiceManager.getInstance().consumeService(HeartBeatService.class, resultHandler);
ServiceManager.getInstance().consumeService(HeartBeatService.class, "SOME_ADDRESS", resultHandler);
```
##### UnPublish:

```java
ServiceManager.getInstance().unPublishService(HeartBeatService.class, Record service);
ServiceManager.getInstance().unPublishService("SOME_ADDRESS", Record service);
```
or
```java
ServiceManager.getInstance().unPublishService(HeartBeatService.class, Record service, resultHandler);
ServiceManager.getInstance().unPublishService("SOME_ADDRESS", Record service, resultHandler);
```

#### API's

##### Publish:

```java
ServiceManager.getInstance().publishApi(httpRecord);
```
or
```java
ServiceManager.getInstance().publishApi(httpRecord, resultHandler);
```
##### Consume:

```java
ServiceManager.getInstance().consumeApi(nameOfRecord, resultHandler);
```
##### UnPublish:

```java
ServiceManager.getInstance().unPublishApi(record, resultHandler);
```

# 1.1 Usage of ApiManager

## ApiManager

The ApiManager class is a helper for constructing HttpRecords for the ServiceManager. It accepts an object implementing the HostProducer interface to be able to distinguish between internal and external traffic, in the case of f.ex. a load balancer on an internal network.

#### Creating an internal record

SSL defaults to true, can be appended as last parameter to set it manually.
```java
ServiceManager.getInstance().publishApi(apiManager.createInternalRecord("SOME_API", "/api"));
```
#### Creating an external record

SSL defaults to true, can be appended as last parameter to set it manually.
```java
ServiceManager.getInstance().publishApi(apiManager.createExternalRecord("SOME_API", "/api"));
```

#### Generic request with circuitbreaker support

You can use the performRequestWithCircuitBreaker method to do a call to any particular path.