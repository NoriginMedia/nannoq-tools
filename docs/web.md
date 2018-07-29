Table of Contents
=================

   * [Table of Contents](#table-of-contents)
   * [1.0 Controller Implementation](#10-controller-implementation)
         * [Controller implementation](#controller-implementation)
      * [1.0.1 Intercepting executions in the controller](#101-intercepting-executions-in-the-controller)
         * [Intercept methods:](#intercept-methods)
            * [Show](#show)
            * [Index](#index)
            * [Create](#create)
            * [Update](#update)
            * [Delete](#delete)
   * [1.1 Querying the Controller](#11-querying-the-controller)
         * [Querying](#querying)
            * [Show](#show-1)
            * [Index](#index-1)
      * [1.1.1 Filtering](#111-filtering)
         * [Filtering](#filtering)
            * [Examples](#examples)
                  * [Above examples as Url Encoded Json. (commentCount as field)](#above-examples-as-url-encoded-json-commentcount-as-field)
      * [1.1.2 Ordering](#112-ordering)
            * [Ordering](#ordering)
               * [Examples](#examples-1)
                  * [Above examples as Url Encoded Json. (commentCount as field)](#above-examples-as-url-encoded-json-commentcount-as-field-1)
      * [1.1.3 Aggregation](#113-aggregation)
            * [Aggregation](#aggregation)
                  * [Query Examples](#query-examples)
                  * [Above examples as Url Encoded Json. (commentCount as field)](#above-examples-as-url-encoded-json-commentcount-as-field-2)
      * [1.1.4 Projection](#114-projection)
            * [Projection](#projection)
               * [Examples](#examples-2)
                  * [Above examples as Url Encoded Json.](#above-examples-as-url-encoded-json)
   * [1.2 Cross Model Querying](#12-cross-model-querying)
      * [1.2.1 Cross Model Aggregations](#121-cross-model-aggregations)
      * [Cross-Model Aggregation](#cross-model-aggregation)
               * [Query Examples](#query-examples-1)
                  * [Above examples as Url Encoded Json.](#above-examples-as-url-encoded-json-1)
               * [Query Examples](#query-examples-2)
                  * [Above examples as Url Encoded Json.](#above-examples-as-url-encoded-json-2)
               * [Query Examples](#query-examples-3)
                  * [Above examples as Url Encoded Json.](#above-examples-as-url-encoded-json-3)

# 1.0 Controller Implementation

### Controller implementation

A clean implementation expects two parameters, and accepts two optional parameters.

- The vertx app configuration
    - For Redis backed etag storage, the configuration should have a parameter "redis_host", and an optional "redis_port". If not etags will not be stored remotely and will be checked for on the fly, before producing a response.
- A Repository implementation that is typed to the class used for the RestControllerImpl.
- An optional Function that reads the RoutingContext and returns a valid JsonObject based on the path, this is implemented by the client implementation. The default reads all path params.

The method postVerifyNotExists method should be overriden to set ids from the path if necessary. E.g. with a DynamoDB implemenation.

You can either extend the controller to do specific overrides like this example:

```java
public class TestModelRESTController extends RestControllerImpl<TestModel> {
    public TestModelRESTController(JsonObject appConfig, Repository<TestModel> repository) {
        super(TestModel.class, appConfig, repository);
    }
}
```

or use the RestControllerImpl class directly for a standard REST controller (DynamoDBRepository as example):

```java
Repository<TestModel> repository = new DynamoDBRepository<>(TestModel.class, config());
RestControllerImpl<TestModel> = new RestControllerImpl<TestModel>(TestModel.class, config(), repository);
```

## 1.0.1 Intercepting executions in the controller

The controller can be overriden at any point in its execution to perform particular business logic. When overriding it's methods, remember to call the next method in the chain to continue execution. This enables async operations at any point in execution.

### Intercept methods:

#### Show

```java
default void preShow(RoutingContext routingContext) {
    performShow(routingContext);
}
```
#### Index

```java
default void preIndex(RoutingContext routingContext, String customQuery) {
    prepareQuery(routingContext, customQuery);
}

default void preProcessQuery(RoutingContext routingContext, Map<String, List<String>> queryMap) {
    processQuery(routingContext, queryMap);
}

default void postProcessQuery(RoutingContext routingContext, AggregateFunction aggregateFunction,
                              Queue<OrderByParameter> orderByQueue, Map<String, List<FilterParameter>> params,
                              @Nonnull String[] projections, String indexName, Integer limit) {
    postPrepareQuery(routingContext, aggregateFunction, orderByQueue, params, projections, indexName, limit);
}

default void postPrepareQuery(RoutingContext routingContext, AggregateFunction aggregateFunction,
                              Queue<OrderByParameter> orderByQueue, Map<String, List<FilterParameter>> params,
                              String[] projections, String indexName, Integer limit) {
    createIdObjectForIndex(routingContext, aggregateFunction, orderByQueue, params, projections, indexName, limit);
}

```
#### Create

```java
default void preCreate(RoutingContext routingContext) {
    if (denyQuery(routingContext)) return;

    parseBodyForCreate(routingContext);
}

default void preVerifyNotExists(E newRecord, RoutingContext routingContext) {
    verifyNotExists(newRecord, routingContext);
}

default void postVerifyNotExists(E newRecord, RoutingContext routingContext) {
    preSetIdentifiers(newRecord, routingContext);
}

default void preSetIdentifiers(E newRecord, RoutingContext routingContext) {
    setIdentifiers(newRecord, routingContext);
}

default void preSanitizeForCreate(E record, RoutingContext routingContext) {
    performSanitizeForCreate(record, routingContext);
}

default void performSanitizeForCreate(E record, RoutingContext routingContext) {
    record.sanitize();

    postSanitizeForCreate(record, routingContext);
}

default void postSanitizeForCreate(E record, RoutingContext routingContext) {
    preValidateForCreate(record, routingContext);
}

default void preValidateForCreate(E record, RoutingContext routingContext) {
    performValidateForCreate(record, routingContext);
}

default void postValidateForCreate(E record, RoutingContext routingContext) {
    performCreate(record, routingContext);
}

default void postCreate(@Nonnull E createdRecord, RoutingContext routingContext) {
    long initialNanoTime = routingContext.get(REQUEST_PROCESS_TIME_TAG);

    routingContext.response().putHeader(HttpHeaders.CONTENT_TYPE, "application/json; charset=utf-8");
    routingContext.put(BODY_CONTENT_TAG, createdRecord.toJsonString());
    setStatusCodeAndContinue(201, routingContext, initialNanoTime);
}
```
#### Update

```java
default void preUpdate(RoutingContext routingContext) {
    if (denyQuery(routingContext)) return;

    parseBodyForUpdate(routingContext);
}

default void preVerifyExistsForUpdate(E newRecord, RoutingContext routingContext) {
    verifyExistsForUpdate(newRecord, routingContext);
}

default void postVerifyExistsForUpdate(E oldRecord, E newRecord, RoutingContext routingContext) {
    preSanitizeForUpdate(oldRecord, newRecord, routingContext);
}

default void preSanitizeForUpdate(E record, E newRecord, RoutingContext routingContext) {
    performSanitizeForUpdate(record, newRecord, routingContext);
}

default void performSanitizeForUpdate(E record, E newRecord, RoutingContext routingContext) {
    Function<E, E> setNewValues = rec -> {
        rec.setModifiables(newRecord);
        rec.sanitize();

        return rec;
    };

    postSanitizeForUpdate(setNewValues.apply(record), setNewValues, routingContext);
}

default void postSanitizeForUpdate(E record, Function<E, E> setNewValues, RoutingContext routingContext) {
    preValidateForUpdate(record, setNewValues, routingContext);
}

default void preValidateForUpdate(E record, Function<E, E> setNewValues, RoutingContext routingContext) {
    performValidateForUpdate(record, setNewValues, routingContext);
}

default void postValidateForUpdate(E record, Function<E, E> setNewValues, RoutingContext routingContext) {
    performUpdate(record, setNewValues, routingContext);
}

void performUpdate(E updatedRecord, Function<E, E> setNewValues, RoutingContext routingContext);

default void postUpdate(@Nonnull E updatedRecord, RoutingContext routingContext) {
    long initialNanoTime = routingContext.get(REQUEST_PROCESS_TIME_TAG);

    routingContext.response().putHeader(HttpHeaders.CONTENT_TYPE, "application/json; charset=utf-8");
    routingContext.put(BODY_CONTENT_TAG, updatedRecord.toJsonString());
    setStatusCodeAndContinue(200, routingContext, initialNanoTime);
}
```
#### Delete

```java
default void preDestroy(RoutingContext routingContext) {
    if (denyQuery(routingContext)) return;

    verifyExistsForDestroy(routingContext);
}

void verifyExistsForDestroy(RoutingContext routingContext);

default void postVerifyExistsForDestroy(E recordForDestroy, RoutingContext routingContext) {
    performDestroy(recordForDestroy, routingContext);
}

default void postDestroy(@Nonnull E destroyedRecord, RoutingContext routingContext) {
    long initialNanoTime = routingContext.get(REQUEST_PROCESS_TIME_TAG);

    setStatusCodeAndContinue(204, routingContext, initialNanoTime);
}
```

# 1.1 Querying the Controller

### Querying

#### Show 

Show operations returns the JSON representation of a single object.

#### Index

All index operations on the API can be performed with finegrained filtering and ordering as well as aggregation with or without grouping, for doing more advanced searches.

Index operations return an itemList with the following format:

```javascript
{
    "etag" : "string",
    "pageToken" : "string",
    "count" : "integer",
    "items" : "array of objects in JSON representation"
}
```

## 1.1.1 Filtering

### Filtering

 * Filtering can be performed on any fields of an object. 
 * All filtering is defined by url encoded json and can be chained for doing multiple filtering operations on a single field.
 * Available filtering options are:
   * eq (Equals)
   * ne (Not Equals)
   * gt (Greater Than)
   * lt (Less Than)
   * ge (Greater or Equal Than)
   * le (Lesser or Equal Than)
   * contains (Field contains value)
   * notContains (Field does not contain value)
   * beginsWith (Field begins with value (CASE SENSITIVE))
   * in (Value exists in field)
   * type (Boolean type (And / Or))

#### Examples

```javascript
{
  "eq":15000
}


[{
  "gt":10000
}, {
  "lt":5000,
  "type":"or"
}]


[{
  "gt":10000
}, {
  "lt":20000,
  "type":"and"
}] 
```

###### Above examples as Url Encoded Json. (commentCount as field)
 commentCount=%7B%22eq%22%3A15000%7D 

 commentCount=%5B%7B%22gt%22%3A10000%7D%2C%7B%22lt%22%3A5000%2C%22type%22%3A%22or%22%7D%5D 

 commentCount=%5B%7B%22gt%22%3A10000%7D%2C%7B%22lt%22%3A20000%2C%22type%22%3A%22and%22%7D%5D
 
## 1.1.2 Ordering


#### Ordering

  * Ordering can be performed on any indexed fields of an object. Typically numerical or date fields.
  * All ordering is defined by url encoded json.
  * You can only order on a single index.
  * Available ordering options are:
    * field (Field to order by)
    * direction ("asc" or "desc", DESC is default)

##### Examples

```javascript
{
  "field":"commentCount"
}


{
  "field":"commentCount",
  "direction":"asc"
} 
```

###### Above examples as Url Encoded Json. (commentCount as field)
 orderBy=%7B%22field%22%3A%22commentCount%22%7D 

 orderBy=%7B%22field%22%3A%22commentCount%22%2C%22direction%22%3A%22asc%22%7D

## 1.1.3 Aggregation

#### Aggregation

  * Aggregation can be performed on any fields that are numerical, whole or decimal.
  * All aggregation is defined by url encoded json.
  * You can only aggregate on a single field.
  * Filtering is available for all functions.
  * Ordering is not available for AVG, SUM and COUNT.
  * GroupBy can be performed on a single field basis.
  * Available aggregatin options are:
    * field (Field to aggregate on, if any. Not available for COUNT.)
    * function (MIN, MAX, AVG, SUM, COUNT)
    * groupBy (List of Grouping Configurations, currently limited to 3.)
      * groupBy (Field to group by, e.g. userId)
      * groupByUnit (Direction of value sort, asc or desc. DESC is default)
      * groupByRange (Count of objects to return, max 100, min 1, default 10)
      * groupingSortOrder (Direction of value sort, asc or desc. DESC is default)
      * groupingListLimit (Count of objects to return, max 100, min 1, default 10)
    * MIN and MAX will return an array of any corresponding objects. E.g the FeedItems with the MAX likeCount.
    * AVG, SUM, COUNT will return a JsonObject with the format of:

```javascript
{
  "count": "<integer>",
  "results": [
      "groupByKey":"<valueOfGroupByKeyElement>",
      "<aggregateFunction>":"<aggregateValue>"
    ]
} 
```

  * Non-grouped responses

```javascript
{
  "avg":"<avgValue>"
}

{
  "sum":"<sumValue>"
}

{
  "count":"<countValue>"
} 
```

###### Query Examples

```javascript
{
  "field":"commentCount",
  "function":"MIN"
}

{
  "field":"likeCount",
  "function":"COUNT",
  "groupBy": [
    {
      "groupBy":"userId"
    }
  ]
}

{
  "function":"COUNT"
} 
```

###### Above examples as Url Encoded Json. (commentCount as field)
 aggregate=%7B%22field%22%3A%22commentCount%22%2C%22function%22%3A%22MIN%22%7D

 aggregate=%7B%22field%22%3A%22likeCount%22%2C%22function%22%3A%22COUNT%22%2C%22groupBy%22%3A%5B%7B%22groupBy%22%3A%22userId%22%7D%5D%7D

 aggregate=%7B%22function%22%3A%22COUNT%22%7D

## 1.1.4 Projection


 #### Projection

  * Projection can and should be performed wherever possible to ensure you only receive the specific data you need.
  * You can still do filtering and ordering as usual regardless of projected fields, you can f.ex. do filtering on an attribute, without projecting it.
  * Projection is defined by an url encoded json.
  * Projection can be performed on all operations, except AVG, SUM and COUNT aggregations, they are automatically projected for performance.
  * You can project on any attribute. Key Values will always be projected, regardless of projection-selection on Index routes, they must be projected to generate pageTokens regardless.
  * Available projection options are:
    * fields (String array of fields to project)

```javascript
{
  "fields":["someField","someOtherField","someThirdField"]
} 
```

##### Examples

```javascript
{
  "fields":["feedId"]
}

{
  "fields":["providerFeedItemId","likeCount","feedId"]
} 
```

###### Above examples as Url Encoded Json.
 projection=%7B%22fields%22%3A%5B%22feedId%22%5D%7D

 projection=%7B%22fields%22%3A%5B%22providerFeedItemId%22%2C%22likeCount%22%2C%22feedId%22%5D%7D

# 1.2 Cross Model Querying

## 1.2.1 Cross Model Aggregations


## Cross-Model Aggregation

  * All Cross-Model aggregations are automatically scoped to the request feedId, any filtering is on top of this.
  * Aggregation can be performed on any fields that are numerical, whole or decimal, across models.
  * All aggregation is defined by url encoded json.
  * You can aggregate on multiple fields from different models.
  * There is no projection of fields for output.
  * GroupBy can be performed on a single field basis.
  * Output is similar to single-model aggregation.
  * Available aggregate query options are:
    * function (AVG, SUM, COUNT)
    * groupBy (List of Cross Model Grouping Configurations, currently limiited to 1.)
    * groupBy (Array of strings defining field(s) to group by. If the field is equal across models a singular shorthand like ["userId"] can be used. For varying you must prepend by model name pluralized, e.g. ["comments.providerId","likes.likeObjectId"])
    * groupByUnit (Direction of value sort, asc or desc. DESC is default, this is applied to every groupBy element across models)
    * groupByRange (Count of objects to return, max 100, min 1, default 10, this is applied to every groupBy element across models)
    * groupingSortOrder (Direction of value sort, asc or desc. DESC is default, this is applied to every groupBy element across models)
    * groupingListLimit (Count of objects to return, max 100, min 1, default 10, this is applied to every groupBy element across models)
    * groupByUnit (Unit to Range Grouping, available values are: INTEGER (For numbers), DATE (For dates), default is nothing. Only number and date fields are supported)
    * groupByRange (Amount to range-group upon, available values are: Some x integer for numbers, HOUR, TWELVE_HOUR, DAY, WEEK, MONTH, YEAR for Dates.
    * groupBySortOrder (Direction of value sort, asc or desc. DESC is default)
    * groupByListLimit (Count of objects to return, max 100, min 1, default 10)

##### Query Examples

```javascript
{
  "function":"COUNT",
  "groupBy": [
    {
      "groupBy": ["userId"]
    }
  ]
}

{
  "function":"SUM",
  "groupBy": [
    {
      "groupBy": ["feedId"],
      "groupingSortOrder":"asc",
      "groupingListLimit":10
    }
  ]
}

{
  "function":"COUNT",
  "groupBy": [
    "groupBy":["comments.registrationDate", "likes.createdAt"],
    "groupByUnit":"DATE",
    "groupByRange":"WEEK",
    "groupingSortOrder":"asc",
    "groupingListLimit":10
  ]
} 
```

###### Above examples as Url Encoded Json.
 aggregate=%7B%22function%22%3A%22COUNT%22%2C%22groupBy%22%3A%5B%7B%22groupBy%22%3A%5B%22userId%22%5D%7D%5D%7D

 aggregate=%7B%22function%22%3A%22SUM%22%2C%22groupBy%22%3A%5B%7B%22groupBy%22%3A%5B%22feedId%22%5D%2C%22groupBySortOrder%22%3A%22asc%22%2C%22groupByListLimit%22%3A10%7D%5D%7D

 aggregate=%7B%22function%22%3A%22COUNT%22%2C%22groupBy%22%3A%5B%22groupBy%22%3A%5B%22comments.registrationDate%2Clikes.createdAt%22%5D%2C%22groupByUnit%22%3A%22DATE%22%2C%22groupByRange%22%3A%22WEEK%22%2C%22groupBySortOrder%22%3A%22asc%22%2C%22groupByListLimit%22%3A10%5D%7D

  * Available projection query options are: 
    * models (String array of models to aggregate upon, must be pluralized)
    * fields (String array of fields to do aggregation on, prepended by pluralized model name)
      * fields must be empty for COUNT operations, due to being irrelevant

##### Query Examples

```javascript
{
  "models":["comments", "likes"]
}

{
  "models":["feedItems", "comments"],
  "fields":["feedItems.likeCount","comments.likeCount"]
} 
``` 

###### Above examples as Url Encoded Json.
 projection=%7B%22models%22%3A%5B%22comments%22%2C%20%22likes%22%5D%7D

 projection=%7B%22models%22%3A%5B%22feedItems%22%2C%20%22comments%22%5D%2C%22fields%22%3A%5B%22feedItems.likeCount%22%2C%22comments.likeCount%22%5D%7D

  * Available filter query options are: 
    * models (Object array of models to filter upon)
      * model (String name of model to filter upon, pluralized.
      * fields (Object array of fields to filtre upon)
        * field (String name of field to fiter upon)
        * parameters (Object array of filtering parameters, params are same as normal filtering)

##### Query Examples

```javascript
{
  "models":[
    {
      "model":"feedItems",
      "fields":[
        {
          "field":"providerName",
          "parameters":[
            {
              "eq":"FACEBOOK"
            }
          ]
        }
      ]
    }
  ]
}

{
  "models":[
    {
      "model":"comments",
      "fields":[
        {
          "field":"reply",
          "parameters":[
            {
              "eq":true
            }
          ]
        },
        {
          "field":"likeCount",
          "parameters":[
            {
              "gt":10000
            },
            {
              "lt":50000
            }
          ]
        }
      ]
    },
    {
      "model":"likes",
      "fields":[
        {
          "field":"userId",
          "parameters":[
            {
              "eq":"4554b1eda02f902beea73cd03c4acb4"
            },
            {
              "eq":"6ab6c2a487d25c6c314774a845690e6",
              "type":"or"
            }
          ]
        }
      ]
    }
  ]
} 
```  

###### Above examples as Url Encoded Json.
 projection=%7B%22models%22%3A%5B%7B%22model%22%3A%22feedItems%22%2C%22fields%22%3A%5B%7B%22field%22%3A%22providerName%22%2C%22parameters%22%3A%5B%7B%22eq%22%3A%22FACEBOOK%22%20%7D%5D%7D%5D%7D%5D%7D

 projection=%7B%22models%22%3A%5B%7B%22model%22%3A%22comments%22%2C%22fields%22%3A%5B%7B%22field%22%3A%22reply%22%2C%22parameters%22%3A%5B%7B%22eq%22%3Atrue%7D%5D%7D%2C%7B%22field%22%3A%22likeCount%22%2C%22parameters%22%3A%5B%7B%22gt%22%3A10000%7D%2C%7B%22lt%22%3A50000%7D%5D%7D%5D%7D%2C%7B%22model%22%3A%22likes%22%2C%22fields%22%3A%5B%7B%22field%22%3A%22userId%22%2C%22parameters%22%3A%5B%7B%22eq%22%3A%224554b1eda02f902beea73cd03c4acb4%22%7D%2C%7B%22eq%22%3A%226ab6c2a487d25c6c314774a845690e6%22%2C%22type%22%3A%22or%22%7D%5D%7D%5D%7D%5D%7D