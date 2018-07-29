Table of Contents
=================

   * [1.0 Authentication](#10-authentication)
      * [1.0.1 AuthenticationService](#101-authenticationservice)
         * [AuthenticationService Interface](#authenticationservice-interface)
      * [1.0.2 VerificationService](#102-verificationservice)
         * [VerificationService Interface](#verificationservice-interface)
      * [1.0.3 AuthPackageHandler](#103-authpackagehandler)
         * [AuthPackageHandler Interface](#authpackagehandler-interface)
      * [1.0.4 Providers](#104-providers)
      * [1.0.4.1 Facebook](#1041-facebook)
         * [Facebook](#facebook)
      * [1.0.4.2 Google](#1042-google)
         * [Google](#google)
      * [1.0.4.3 Instagram](#1043-instagram)
         * [Instagram](#instagram)
   * [1.1 Authorization](#11-authorization)
      * [1.1.1 Authorizer](#111-authorizer)
         * [Authorizer Interface](#authorizer-interface)
      * [1.1.2 Authorization Class](#112-authorization-class)
         * [Authorization Class](#authorization-class)
   * [1.2 Web Handlers](#12-web-handlers)
      * [1.2.1 ApiKeyHandler](#121-apikeyhandler)
         * [ApiKeyHandler](#apikeyhandler)
      * [1.2.2 AuthHandler](#122-authhandler)
         * [AuthHandler](#authhandler)
      * [1.2.3 JWTGenerator](#123-jwtgenerator)
         * [JWTGenerator](#jwtgenerator)
      * [1.2.4 JWTReceiver](#124-jwtreceiver)
         * [JWTReceiver](#jwtreceiver)
   * [1.3 AuthUtils](#13-authutils)
         * [AuthUtils](#authutils)
         
# 1.0 Authentication

## 1.0.1 AuthenticationService

### AuthenticationService Interface

Implementations of the AuthenticationService answer to the following contract:

```java
@ProxyGen
@VertxGen
public interface AuthenticationService {
    @Fluent
    AuthenticationService createJwtFromProvider(@Nonnull String token, @Nonnull String authProvider,
                                                @Nonnull Handler<AsyncResult<AuthPackage>> resultHandler);

    @Fluent
    AuthenticationService refresh(@Nonnull String refreshToken,
                                  @Nonnull Handler<AsyncResult<TokenContainer>> resultHandler);

    @Fluent
    @GenIgnore
    default AuthenticationService switchToAssociatedDomain(String domainId, Jws<Claims> verifyResult,
                                                           Handler<AsyncResult<TokenContainer>> resultHandler) {
        resultHandler.handle(Future.failedFuture(new NotImplementedException()));

        return this;
    }

    @ProxyClose
    void close();
}
```

An implementation is expect to produce JWT's based on the token received for an external provider as defined by the Provider interface. It should also be able to refresh an outdated token as a result of an incoming refreshToken. Lastly it should have an implementation for switching from one domain to another, generating a new JWT.

The default implementation AuthenticationServiceImpl is whats used by the JWTGenerator class and can also be deployed as a service on the cluster. It is also reliant on Redis and expects the following parameter in its config:

```javascript
{
    "redis_host" : "<string>",
    "redis_port" : "<integer>" // optional
}
```

## 1.0.2 VerificationService

### VerificationService Interface

The verification interface is responsible for checking the validity of an incoming JWT and checking for authorization of the indicated request. It is called directly by the JWTReceiver and additional can be deployed as a service on the cluster with its default implementation VerificationServiceImpl. It's contract is as follows:

```java
@ProxyGen
@VertxGen
public interface VerificationService {
    @Fluent
    VerificationService verifyJWT(@Nonnull String token, @Nonnull Authorization authorization,
                                  @Nonnull Handler<AsyncResult<VerifyResult>> resultHandler);

    @Fluent
    VerificationService revokeToken(@Nonnull String token, @Nonnull Handler<AsyncResult<Boolean>> resultHandler);
    
    @Fluent
    VerificationService verifyJWTValidity(@Nonnull Handler<AsyncResult<Boolean>> resultHandler);

    @Fluent
    VerificationService revokeUser(@Nonnull String userId, @Nonnull Handler<AsyncResult<Boolean>> resultHandler);

    @Fluent
    @GenIgnore
    default VerificationService verifyToken(@Nonnull String token, Handler<AsyncResult<Jws<Claims>>> resultHandler) {
        resultHandler.handle(Future.failedFuture(new NotImplementedException()));

        return this;
    }

    @Fluent
    @GenIgnore
    default VerificationService verifyAuthorization(Jws<Claims> claims, Authorization authorization,
                                                    Handler<AsyncResult<Boolean>> resultHandler)
            throws IllegalAccessException {
        resultHandler.handle(Future.failedFuture(new NotImplementedException()));

        return this;
    }

    @ProxyClose
    void close();
}
```

The default implementation is generic in use for all applications in conjunction with the JWTReceiver. It is also reliant on Redis and expects the following parameter in its config:

```javascript
{
    "redis_host" : "<string>",
    "redis_port" : "<integer>" // optional
}
```

It can also be set to dev mode in which case it will verifying without checking validity in redis.

## 1.0.3 AuthPackageHandler

### AuthPackageHandler Interface

This interface is intended to be implemented by the application to handle the final conversion of an external token to an internal JWT, and pass the userprofile to the handler. Its contract is as follows:

```java
public interface AuthPackageHandler {
    void processDirectAuth(AuthPackage authPackage, String userId,
                           Handler<AsyncResult<JsonObject>> resultHandler);

    void processOAuthFlow(AuthPackage authPackage, String userId,
                          String finalUrl, Handler<AsyncResult<JsonObject>> resultHandler);
}
```

## 1.0.4 Providers

This section details the various providers supported.

They are all based on the Provider interface for checking an external Token and converting it to either a UserProfile or a payload.

```java
public interface Provider<T> {
    void checkJWT(String token, Handler<AsyncResult<T>> resultHandler);
}
```

## 1.0.4.1 Facebook

### Facebook

The Facebook provider expects two parameters in its config input. The appId and appSecret of the facebook app that corresponds to the application.

```java
appConfig.getString("faceBookAppId");
appConfig.getString("faceBookAppSecret");
```

## 1.0.4.2 Google

### Google

This provider verifies google tokens and returns a Google Payload for consumption. It requires an array of valid google oauth ids for login.

```java
appConfig.getJsonArray("gcmIds").getList();
```

## 1.0.4.3 Instagram

### Instagram

This provider verifies an incoming token and returns an appropriate UserProfile. It requries an instagram client id and secret as parameters in its configuration.

```java
appConfig.getString("instaClientId");
appConfig.getString("instaClientSecret");
```

# 1.1 Authorization

## 1.1.1 Authorizer

### Authorizer Interface

The Authorizer is optional for implementing an authorizer class. It is used by the VerificationServiceImpl.

```java
public interface Authorizer {
    boolean isAsync();
    boolean authorize(Jws<Claims> claims, String domainIdentifier, Authorization authorization) throws IllegalAccessException;
    void authorize(Jws<Claims> claims, String domainIdentifier, Authorization authorization, Handler<AsyncResult<Boolean>> resultHandler);
    void block(String domainIdentifier, String userId, Handler<AsyncResult<Boolean>> resultHandler);
}

```

## 1.1.2 Authorization Class

### Authorization Class

The Authorization class is used for producing a simple auth model.

```java
@DataObject(generateConverter = true)
public class Authorization {
    private String model;
    private String method;
    private String domainIdentifier;

    public Authorization() {}

    public Authorization(JsonObject jsonObject) {
        fromJson(jsonObject, this);
    }

    public JsonObject toJson() {
        return JsonObject.mapFrom(this);
    }

    public boolean validate() {
        return (domainIdentifier != null &&
                (domainIdentifier.equals(VALIDATION_REQUEST) || domainIdentifier.equals(GLOBAL_AUTHORIZATION)) ||
                (model != null && method != null && domainIdentifier != null));
    }

    public String getModel() {
        return model;
    }

    @Fluent
    public Authorization setModel(String model) {
        this.model = model;

        return this;
    }

    public String getMethod() {
        return method;
    }

    @Fluent
    public Authorization setMethod(String method) {
        this.method = method;

        return this;
    }

    public String getDomainIdentifier() {
        return domainIdentifier;
    }

    @Fluent
    public Authorization setDomainIdentifier(String domainIdentifier) {
        this.domainIdentifier = domainIdentifier;

        return this;
    }

    public static Authorization global() {
        return new Authorization().setDomainIdentifier(VALIDATION_REQUEST);
    }
}
```

# 1.2 Web Handlers

## 1.2.1 ApiKeyHandler

### ApiKeyHandler

This simple class accepts an apikey as a parameter. When deployed as a handler it will verify if the Authorization HTTP Header contains a key starting with "APIKEY " and then check equality on the supplied key. If not equal it will fail the routingContext with a 401.

## 1.2.2 AuthHandler

### AuthHandler

This Handler is a default implemenation that leverages the AuthUtils class and the VerificationService. It will check for both ApiKeys and JWT tokens by "Bearer " prepended token in the Authorization Header.

## 1.2.3 JWTGenerator

### JWTGenerator

This class is used for accepting external tokens for conversion and for the typical Oauth Flow.

OAuth:

returnAuthUrl expects a path parameter called :provider, e.g. google.

```java
public void returnAuthUrl(RoutingContext routingContext) { ... }
public void handle(RoutingContext routingContext) { ... }
```

Direct Conversion:

This expects two headers set:

 - Authorization (The actual Token, prepended with "Bearer ")
 - X-Authorization-Provider (Caps Provider, e.g. GOOGLE)

```java
public void directAuth(RoutingContext routingContext) { ... }
```

Refreshing Tokens:

This expects the token in the Authorization Header, prepended with "Bearer ".

```java
public void refreshFromHttp(RoutingContext routingContext) { ... }
```

## 1.2.4 JWTReceiver

### JWTReceiver

This class is used for handling incoming JWT's.

The default handler expects an Authorization class as base64 encoded JSON in a custom header. The default is:

X-Authorization-Type

```java
public void handle(RoutingContext routingContext) { ... }
```

It can also be used to revoke tokens:

The token should be in the Authorization Header, just as a normal request. This will also revoke the refresh token.

```java
public void revoke(RoutingContext routingContext) { ... }
```

# 1.3 AuthUtils

### AuthUtils

This class has two public apis that abstract away robust communication with both the AuthenticationService and the VerificationService. If you wish to convert an external token you can call this method:

```java
public AuthUtils convertExternalToken(String token, String provider, Handler<AsyncResult<AuthPackage>> resultHandler) { ... }
```

If you wish to authorize a request you can call this:

```java
public AuthUtils authenticateAndAuthorize(String jwt, Authorization authorization, Handler<AsyncResult<VerifyResult>> resultHandler) { ... }
```

