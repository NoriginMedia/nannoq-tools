Table of Contents
=================

   * [1.0 Repository](#10-repository)
      * [1.0.0 Repository Interface](#100-repository-interface)
      * [Repository Interface](#repository-interface)
         * [Parameters](#parameters)
         * [Create](#create)
         * [Read](#read)
            * [Show](#show)
            * [Index](#index)
            * [Aggregations](#aggregations)
         * [Update](#update)
            * [SQL and NoSQL](#sql-and-nosql)
         * [Delete](#delete)
      * [1.0.1 ETagManager](#101-etagmanager)
         * [ETagManager](#etagmanager)
         * [Implementations](#implementations)
               * [InMemoryEtagManagerImpl](#inmemoryetagmanagerimpl)
               * [RedisETagManagerImpl](#redisetagmanagerimpl)
      * [1.0.2 CacheManager](#102-cachemanager)
         * [CacheManager Interface](#cachemanager-interface)
         * [Implementations](#implementations-1)
               * [ClusterCacheManagerImpl](#clustercachemanagerimpl)
               * [LocalCacheManagerImpl](#localcachemanagerimpl)
      * [1.0.3 RedisUtils](#103-redisutils)
         * [RedisUtils](#redisutils)
   * [1.1 Data Model](#11-data-model)
      * [1.1.0 Model](#110-model)
         * [Model Interface](#model-interface)
      * [1.1.1 ETagable](#111-etagable)
         * [ETagable Interface](#etagable-interface)
      * [1.1.2 Cacheable](#112-cacheable)
         * [Cacheable Interface](#cacheable-interface)
      * [1.1.3 ModelUtils](#113-modelutils)
         * [ModelUtils](#modelutils)
   * [1.2 Implementations](#12-implementations)
      * [1.2.1 DynamoDBRepository](#121-dynamodbrepository)
         * [1.2.1.0 Implementation](#1210-implementation)
         * [Implementation](#implementation)
         * [ETagManager](#etagmanager-1)
         * [Custom Managers](#custom-managers)
         * [Constructors](#constructors)
         * [Queries](#queries)
            * [1.2.1.1 DynamoDBModel](#1211-dynamodbmodel)
         * [DynamoDBModel Interface](#dynamodbmodel-interface)
            * [1.2.1.2 ImageUploader](#1212-imageuploader)
         * [ImageUploader Interface](#imageuploader-interface)
            * [1.2.1.3 CachedContent](#1213-cachedcontent)
         * [CachedContent Interface](#cachedcontent-interface)
   * [1.3 Querying](#13-querying)
      * [1.3.0 QueryPack](#130-querypack)
         * [QueryPack](#querypack)
         * [Execution of Query Parameters](#execution-of-query-parameters)
         * [ETags and Caches](#etags-and-caches)
         * [Vert.x Web Integration](#vertx-web-integration)
      * [1.3.1 FilterParameter](#131-filterparameter)
         * [FilterParameter](#filterparameter)
      * [1.3.2 OrderByParameter](#132-orderbyparameter)
         * [OrderByParameter](#orderbyparameter)
      * [1.3.3 AggregateFunction](#133-aggregatefunction)
         * [AggregateFunction](#aggregatefunction)
      * [1.3.4 Projection](#134-projection)
         * [Projection](#projection)

# 1.0 Repository

## 1.0.0 Repository Interface

## Repository Interface

The Repository Interface sets a contract for CRUD operations, and aggregations. This is a shortened list of all available methods, please check the source for a complete list.

### Parameters

All repositories must implement this method, which builds all parameters.

```java
JsonObject buildParameters(Map<String, List<String>> queryMap,
                           Field[] fields, Method[] methods,
                           JsonObject errors,
                           Map<String, List<FilterParameter>> params, int[] limit,
                           Queue<OrderByParameter> orderByQueue,
                           String[] indexName);
```

### Create

```java
default void create(E record, Handler<AsyncResult<CreateResult<E>>> resultHandler) { ... }
default Future<CreateResult<E>> create(E record) { ... }
default void batchCreate(List<E> records, Handler<AsyncResult<List<CreateResult<E>>>> resultHandler) { ... }
default Future<List<CreateResult<E>>> batchCreate(List<E> records) { ... }
```

### Read

Nearly all read operations use a JsonObject parameter called identifiers. This has a structure that is defined by the particular implementation you are using. e.g DynamoDBRepository generally looks something like this:

```javascript
{
    "hash" : "string",
    "range" : "string"
}
``` 

This is up to the implementation and will be defined by them. In addition you can query for multiple ids on index endpoints like this:

```javascript
{
    "multiple" : true,
    "ids" : ["string"]
}
``` 

#### Show

```java
void read(JsonObject identifiers, Handler<AsyncResult<ItemResult<E>>> resultHandler);
default Future<ItemResult<E>> read(JsonObject identifiers) { ... }
void read(JsonObject identifiers, String[] projections, Handler<AsyncResult<ItemResult<E>>> resultHandler);
default void batchRead(Set<JsonObject> identifiers, String[] projections, Handler<AsyncResult<List<ItemResult<E>>>> resultHandler) { ... }
default void batchRead(Set<JsonObject> identifiers, Handler<AsyncResult<List<ItemResult<E>>>> resultHandler) { ... }
default Future<List<ItemResult<E>>> batchRead(List<JsonObject> identifiers) { ... }
default Future<List<ItemResult<E>>> batchRead(List<JsonObject> identifiers, String[] projections) { ... }
```

Some methods are intended for use with NoSQL stores and will ask you for consistency (supports depends on implementation):

```java
default void read(JsonObject identifiers, boolean consistent, Handler<AsyncResult<ItemResult<E>>> resultHandler) { ... }
void read(JsonObject identifiers, boolean consistent, String[] projections, Handler<AsyncResult<ItemResult<E>>> resultHandler);
```
#### Index

Paginated:

```java
default void readAll(String pageToken, Handler<AsyncResult<ItemListResult<E>>> resultHandler) { ... }
default Future<ItemListResult<E>> readAll(String pageToken) { ... }
default void readAll(JsonObject identifiers, QueryPack queryPack, Handler<AsyncResult<ItemListResult<E>>> resultHandler) { ... }
default Future<ItemListResult<E>> readAll(JsonObject identifiers, QueryPack queryPack) { ... }
void readAll(JsonObject identifiers, String pageToken, QueryPack queryPack, String[] projections, Handler<AsyncResult<ItemListResult<E>>> resultHandler);
default Future<ItemListResult<E>> readAll(JsonObject identifiers, String pageToken, QueryPack queryPack, String[] projections) { ... }
void readAll(String pageToken, QueryPack queryPack, String[] projections, Handler<AsyncResult<ItemListResult<E>>> resultHandler);
default Future<ItemListResult<E>> readAll(String pageToken, QueryPack queryPack, String[] projections) { ... }
```

Nonpaginated:

```java
void readAll(Handler<AsyncResult<List<E>>> resultHandler);
default Future<List<E>> readAll() { ... }
void readAll(JsonObject identifiers, Map<String, List<FilterParameter>> filterParameterMap, Handler<AsyncResult<List<E>>> resultHandler);
default Future<List<E>> readAll(JsonObject identifiers, Map<String, List<FilterParameter>> filterParamterMap) { ... }
void readAllWithoutPagination(String identifier, Handler<AsyncResult<List<E>>> resultHandler);
default Future<List<E>> readAllWithoutPagination(String identifier) { ... }
void readAllWithoutPagination(String identifier, QueryPack queryPack, Handler<AsyncResult<List<E>>> resultHandler);
default Future<List<E>> readAllWithoutPagination(String identifier, QueryPack queryPack) { ... }
void readAllWithoutPagination(String identifier, QueryPack queryPack, String[] projections, Handler<AsyncResult<List<E>>> resultHandler);
default Future<List<E>> readAllWithoutPagination(String identifier, QueryPack queryPack, String[] projections) { ... }
```

Read Everything nonpaginated:

```java
default void readAllWithoutPagination(QueryPack queryPack, Handler<AsyncResult<List<E>>> resultHandler) { ... }
default Future<List<E>> readAllWithoutPagination(QueryPack queryPack) { ... }
void readAllWithoutPagination(QueryPack queryPack, String[] projections, Handler<AsyncResult<List<E>>> resultHandler);
default Future<List<E>> readAllWithoutPagination(QueryPack queryPack, String[] projections) { ... }
```

#### Aggregations

```java
default void aggregation(JsonObject identifiers, QueryPack queryPack, Handler<AsyncResult<String>> resultHandler);
default Future<String> aggregation(JsonObject identifiers, QueryPack queryPack);
void aggregation(JsonObject identifiers, QueryPack queryPack, String[] projections, Handler<AsyncResult<String>> resultHandler);
default Future<String> aggregation(JsonObject identifiers, QueryPack queryPack, String[] projections) { ... }
```

The string returned from Aggregation is the JSON representation, it has a similar format to this:

```javascript
{
  "count": "<integer>",
  "results": [
      "groupByKey":"<valueOfGroupByKeyElement>",
      "<aggregateFunction>":"<aggregateValue>"
    ]
} 
```

### Update

#### SQL and NoSQL

The repository is set up to deal with both. For those implementations that operate on optimistic locking, use this method to set an update Function which will be used when fetching a newer version when optimistically locking:

```java
default void update(E record, Function<E, E> updateLogic, Handler<AsyncResult<UpdateResult<E>>> resultHandler) { .. }
default Future<UpdateResult<E>> update(E record, Function<E, E> updateLogic) { ... }
```

If the implementation you are using supports direct updates, like SQL stores you can use these methods:

```java
default void update(E record, Handler<AsyncResult<UpdateResult<E>>> resultHandler) { ... }
default Future<UpdateResult<E>> update(E record) { ... }
```

For batch operations:

```java
default void batchUpdate(Map<E, Function<E, E>> records, Handler<AsyncResult<List<UpdateResult<E>>>> resultHandler) { ... }
default Future<List<UpdateResult<E>>> batchUpdate(Map<E, Function<E, E>> records) { ... }
default void batchUpdate(List<E> records, Handler<AsyncResult<List<UpdateResult<E>>>> resultHandler) { ... }
default Future<List<UpdateResult<E>>> batchUpdate(List<E> records) { ... }
```

### Delete

```java
default void delete(JsonObject identifiers, Handler<AsyncResult<DeleteResult<E>>> resultHandler) { ... }
default Future<DeleteResult<E>> delete(JsonObject identifiers) { ... }
default void batchDelete(List<JsonObject> identifiers, Handler<AsyncResult<List<DeleteResult<E>>>> resultHandler) { ... }
default Future<List<DeleteResult<E>>> batchDelete(List<JsonObject> identifiers) { ... }
```

## 1.0.1 ETagManager

### ETagManager

The ETagManager interface is used to manage etags. This can be passed into certain implementations of the Repository interface for automatically updating etags on mutable operations or it can be used standalone. It only accepts objects which implement both Method and ETagable interfaces.

The interface looks like this:

```java
public interface ETagManager<E extends Model & ETagable> {
    void removeProjectionsEtags(int hash, Handler<AsyncResult<Boolean>> resultHandler);
    void destroyEtags(int hash, Handler<AsyncResult<Boolean>> resultHandler);
    void replaceAggregationEtag(String etagItemListHashKey, String etagKey, String newEtag,
                                Handler<AsyncResult<Boolean>> resultHandler);

    void setSingleRecordEtag(Map<String, String> etagMap, Handler<AsyncResult<Boolean>> resultHandler);
    void setProjectionEtags(String[] projections, int hash, E item);
    void setItemListEtags(String etagItemListHashKey, String etagKey, ItemList<E> itemList, Future<Boolean> itemListEtagFuture);

    void checkItemEtag(String etagKeyBase, String key, String requestEtag, Handler<AsyncResult<Boolean>> resultHandler);
    void checkItemListEtag(String etagItemListHashKey, String etagKey, String etag, Handler<AsyncResult<Boolean>> resultHandler);
    void checkAggregationEtag(String etagItemListHashKey, String etagKey, String etag, Handler<AsyncResult<Boolean>> resultHandler);
}
```
### Implementations

nannoq-repository comes with two premade ETagManagers, InMemoryEtagManagerImpl and RedisETagManagerImpl.

##### InMemoryEtagManagerImpl

This leverages the sharedData structure of Vertx. It uses a LocalMap if vertx is not clustered and a distributed AsyncMap if vert is clustered. This means multiple objects will access the same maps.

##### RedisETagManagerImpl

This leverages an external Redis store.

## 1.0.2 CacheManager

### CacheManager Interface

The CacheManager is used to manage caches for nannoq-repository, and has multiple implementations. Like the ETagManager it is designed to be used by a repository implementation, but can be used standalone. This is the interface definition:

```java
public interface CacheManager<E extends Cacheable & Model> {
    void initializeCache(Handler<AsyncResult<Boolean>> resultHandler);
    void checkObjectCache(String cacheId, Handler<AsyncResult<E>> resultHandler);
    void checkItemListCache(String cacheId, String[] projections, Handler<AsyncResult<ItemList<E>>> resultHandler);
    void checkAggregationCache(String cacheKey, Handler<AsyncResult<String>> resultHandler);

    void replaceCache(Future<Boolean> writeFuture, List<E> records,
                      Function<E, String> shortCacheIdSupplier,
                      Function<E, String> cacheIdSupplier);

    void replaceObjectCache(String cacheId, E item, Future<E> future, String[] projections);
    void replaceItemListCache(String content, Supplier<String> cacheIdSupplier,
                              Handler<AsyncResult<Boolean>> resultHandler);
    void replaceAggregationCache(String content, Supplier<String> cacheIdSupplier,
                                 Handler<AsyncResult<Boolean>> resultHandler);

    void purgeCache(Future<Boolean> future, List<E> records, Function<E, String> cacheIdSupplier);

    Boolean isObjectCacheAvailable();
    Boolean isItemListCacheAvailable();
    Boolean isAggregationCacheAvailable();
}
```

### Implementations

nannoq-repository comes with two premade CacheManagers, ClusterCacheManagerImpl and LocalCacheManagerImpl.

##### ClusterCacheManagerImpl

This leverages JCache to establish a distibuted cache on the vertx cluster. It will look for a configuration for the following three caches:

- objectCache
- itemListCache
- aggregationCache

If they are not found (in cluster.xml for hazelcast f.ex) it will create caches with default settings corresponding to those names.

##### LocalCacheManagerImpl

This leverages a LocalMap extracted from vertx's sharedData for all three caches.

## 1.0.3 RedisUtils

### RedisUtils

By passing in a JsonObject with a "redis_host" field you can get a RedisClient.

```java
public static RedisClient getRedisClient(Vertx vertx, JsonObject config) { ... }
```
You can also supply an optional "redis_port".

# 1.1 Data Model

## 1.1.0 Model

### Model Interface

These are the basic methods of the Model interface that must be implemented by all models. There is no magic here, relatively standard model definition.

```java
public interface Model {
    Model setModifiables(Model newObject);

    @Fluent
    Model sanitize();
    List<ValidationError> validateCreate();
    List<ValidationError> validateUpdate();

    Model setIdentifiers(JsonObject identifiers);

    Date getCreatedAt();
    Model setCreatedAt(Date date);
    Date getUpdatedAt();
    Model setUpdatedAt(Date date);

    @Fluent
    Model setInitialValues(Model record);

    JsonObject toJsonFormat(@Nonnull String[] projections);

    ...
}
```

## 1.1.1 ETagable

### ETagable Interface

This interface asks for a setter and getter for etags, and is not directly concerned with how you store and/or generate it.

```java
String getEtag();
ETagable setEtag(String etag);
```

It also has a default implementation to construct the etag of the object, which ends up in a map which also includes and subobjects that are also ETagable. This can be overriden if you want a hardcoded version of this.

```java
default Map<String, String> generateAndSetEtag(Map<String, String> map) { ... }
```

The last method to override is for generating the unique etag identifier for a particular object:

```java
String generateEtagKeyIdentifier();
```

## 1.1.2 Cacheable

### Cacheable Interface

This is a non-implement interface that creates a cache identifier, that is overrideable if you have a class define two separate logical components. i.e. a Comment class that doubles as a Reply.

```java
public interface Cacheable {
    @DynamoDBIgnore
    @JsonIgnore
    default String getCachePartitionKey() {
        return getClass().getSimpleName();
    }
}
```

## 1.1.3 ModelUtils

### ModelUtils

This is a simple class, currently for creating etags.

```java
public static String hashString(String stringToHash) throws NoSuchAlgorithmException { ... }
public static String returnNewEtag(long tag) { ... }
```

# 1.2 Implementations

## 1.2.1 DynamoDBRepository

### 1.2.1.0 Implementation

### Implementation

Initializing a dynamodbrepository for a chosen Type is easy:

```java
DynamoDBRepository<TestModel> repo = new DynamoDBRepository<>(TestModel.class, new JsonObject());
```

The JsonObject is a configuration object, that expects the following values:

```javascript
{
    "dynamodb_endpoint" : "<string>"
}
```

Simple, right? This is the default though. If you want to leverage the S3 capabilities of the repository you will have to add some additional things:

```javascript
{
    "dynamo_endpoint" : "<string>",
    "dynamo_db_iam_id" : "<string>",
    "dynamo_db_iam_key" : "<string>",
    "content_bucket" : "<string>"
}
```

This intializes the dynamodbrepo for S3 operations. This id and key should ofc correspond to an AWS IAM account that has correct access privileges to the specified bucket. 

S3 specific logic is found in these methods:

```java
public DynamoDBMapper getDynamoDbMapper() {
    return DYNAMO_DB_MAPPER;
}

public static S3Link createS3Link(DynamoDBMapper dynamoDBMapper, String path) {
    return dynamoDBMapper.createS3Link(Region.EU_Ireland, S3BucketName, path);
}

public static String createSignedUrl(DynamoDBMapper dynamoDBMapper, S3Link file) {
    return createSignedUrl(dynamoDBMapper, 7, file);
}

public static String createSignedUrl(DynamoDBMapper dynamoDBMapper, int days, S3Link file) { ... }
```

If you have not added a serializer/deserializer for the S3Link class you can use this method to prepare that, calling it once is enough for all dynamodbrepos.

```java
public static void initializeDynamoDb(JsonObject appConfig, Map<String, Class> collectionMap, Handler<AsyncResult<Void>> resultHandler) {
```

This will initialize the Jackson module for S3Link and you can use the collectionMap parameter to create any tables you need by settings the tableNames, matched with the class you are creating the table for, e.g:

```java
collectionMap.put("feedItems", FeedItem.class);
```

### ETagManager

If you would like to leverage redis for etags you should include the following to the config:

```javascript
{
    "redis_host" : "<string>",
    "redis_port" : "<integer>" //optional
}
```

### Custom Managers

If you want to use custom implementations of the cluster manager or the etag manager you can add those in the constructor:

```java
public DynamoDBRepository(Class<E> type, JsonObject appConfig, @Nullable CacheManager<E> cacheManager) { ... }
public DynamoDBRepository(Class<E> type, JsonObject appConfig, @Nullable ETagManager<E> eTagManager) { ... }
public DynamoDBRepository(Class<E> type, JsonObject appConfig, @Nullable CacheManager<E> cacheManager, @Nullable ETagManager<E> eTagManager) { ... }
```

### Constructors

If you are not on a Vert.x context when constructing the repos you can pass in vertx as the first parameter.

### Queries

On paged queries the DynamoDBRepository expects a Local Secondary Index called PAGINATION_INDEX.

You can also do queries on Global Secondary Indexes with these implementation specific methods:

```java
public void readAll(JsonObject identifiers, QueryPack queryPack, String GSI, Handler<AsyncResult<ItemListResult<E>>> asyncResultHandler) { ... }
public void readAll(JsonObject identifiers, String pageToken, QueryPack queryPack, String[] projections, String GSI, Handler<AsyncResult<ItemListResult<E>>> asyncResultHandler) { ... }
public void aggregation(JsonObject identifiers, QueryPack queryPack, String GSI, Handler<AsyncResult<String>> resultHandler) { ... }
public void aggregation(JsonObject identifiers, QueryPack queryPack, String[] projections, String GSI, Handler<AsyncResult<String>> resultHandler) { ... }
```

#### 1.2.1.1 DynamoDBModel

### DynamoDBModel Interface

This is a simple contract requirement for operationg with the DynamoDBRepository, to return hash and range values for an object. If the model does not have a range key, this will be identified by DynamoDBRepository in which case the getRange() method is redundant, and can just return null.

```java
public interface DynamoDBModel {
    @JsonIgnore
    @DynamoDBIgnore
    String getHash();
    @JsonIgnore
    @DynamoDBIgnore
    String getRange();

    @Fluent
    DynamoDBModel setHash(String hash);

    @Fluent
    DynamoDBModel setRange(String range);
}
```

The following is an example of a fully implemented TestModel class for use with DynamoDBRepository, and a embeddable class which is a DynamoDBDocument.

```java
@DynamoDBTable(tableName="testModels")
@DataObject(generateConverter = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TestModel implements DynamoDBModel, Model, ETagable, Cacheable {
    private String etag;
    private String someStringOne;
    private String someStringTwo;
    private String someStringThree;
    private String someStringFour;
    private Date someDate;
    private Date someDateTwo;
    private Long someLong;
    private Long someLongTwo;
    private Integer someInteger;
    private Integer someIntegerTwo;
    private Boolean someBoolean;
    private Boolean someBooleanTwo;
    private List<TestDocument> documents;
    private Date createdAt;
    private Date updatedAt;
    private Long version;

    public TestModel() {

    }

    public TestModel(JsonObject jsonObject) {
        fromJson(jsonObject, this);

        someDate = jsonObject.getLong("someDate") == null ? null : new Date(jsonObject.getLong("someDate"));
        someDateTwo = jsonObject.getLong("someDateTwo") == null ? null : new Date(jsonObject.getLong("someDateTwo"));
        createdAt = jsonObject.getLong("createdAt") == null ? null : new Date(jsonObject.getLong("createdAt"));
        updatedAt = jsonObject.getLong("updatedAt") == null ? null : new Date(jsonObject.getLong("updatedAt"));
    }

    public JsonObject toJson() {
        return JsonObject.mapFrom(this);
    }

    @Override
    public TestModel setIdentifiers(JsonObject identifiers) {
        setHash(identifiers.getString("hash"));
        setRange(identifiers.getString("range"));

        return this;
    }

    @DynamoDBHashKey
    public String getSomeStringOne() {
        return someStringOne;
    }

    @Fluent
    public TestModel setSomeStringOne(String someStringOne) {
        this.someStringOne = someStringOne;

        return this;
    }

    @DynamoDBRangeKey
    public String getSomeStringTwo() {
        return someStringTwo;
    }

    @Fluent
    public TestModel setSomeStringTwo(String someStringTwo) {
        this.someStringTwo = someStringTwo;

        return this;
    }

    @DynamoDBIndexHashKey(globalSecondaryIndexName = "TEST_GSI")
    public String getSomeStringThree() {
        return someStringThree;
    }

    @Fluent
    public TestModel setSomeStringThree(String someStringThree) {
        this.someStringThree = someStringThree;

        return this;
    }

    public String getSomeStringFour() {
        return someStringFour;
    }

    @Fluent
    public TestModel setSomeStringFour(String someStringFour) {
        this.someStringFour = someStringFour;

        return this;
    }

    @DynamoDBIndexRangeKey(localSecondaryIndexName = PAGINATION_INDEX)
    public Date getSomeDate() {
        return someDate;
    }

    @Fluent
    public TestModel setSomeDate(Date someDate) {
        this.someDate = someDate;

        return this;
    }

    @DynamoDBIndexRangeKey(globalSecondaryIndexName = "TEST_GSI")
    public Date getSomeDateTwo() {
        return someDateTwo;
    }

    @Fluent
    public TestModel setSomeDateTwo(Date someDateTwo) {
        this.someDateTwo = someDateTwo;

        return this;
    }

    public Long getSomeLong() {
        return someLong != null ? someLong : 0L;
    }

    @Fluent
    public TestModel setSomeLong(Long someLong) {
        this.someLong = someLong;

        return this;
    }

    public Long getSomeLongTwo() {
        return someLongTwo != null ? someLongTwo : 0L;
    }

    @Fluent
    public TestModel setSomeLongTwo(Long someLongTwo) {
        this.someLongTwo = someLongTwo;

        return this;
    }

    public Integer getSomeInteger() {
        return someInteger != null ? someInteger : 0;
    }

    @Fluent
    public TestModel setSomeInteger(Integer someInteger) {
        this.someInteger = someInteger;

        return this;
    }

    public Integer getSomeIntegerTwo() {
        return someIntegerTwo != null ? someIntegerTwo : 0;
    }

    @Fluent
    public TestModel setSomeIntegerTwo(Integer someIntegerTwo) {
        this.someIntegerTwo = someIntegerTwo;

        return this;
    }

    public Boolean getSomeBoolean() {
        return someBoolean != null ? someBoolean : Boolean.FALSE;
    }

    @Fluent
    public TestModel setSomeBoolean(Boolean someBoolean) {
        this.someBoolean = someBoolean;

        return this;
    }

    public Boolean getSomeBooleanTwo() {
        return someBooleanTwo != null ? someBooleanTwo : Boolean.FALSE;
    }

    @Fluent
    public TestModel setSomeBooleanTwo(Boolean someBooleanTwo) {
        this.someBooleanTwo = someBooleanTwo;

        return this;
    }

    public List<TestDocument> getDocuments() {
        return documents;
    }

    @Fluent
    public TestModel setDocuments(List<TestDocument> documents) {
        this.documents = documents;

        return this;
    }

    @DynamoDBVersionAttribute
    public Long getVersion() {
        return version;
    }

    @Fluent
    public TestModel setVersion(Long version) {
        this.version = version;

        return this;
    }

    @Override
    public String getHash() {
        return someStringOne;
    }

    @Override
    public String getRange() {
        return someStringTwo;
    }

    @Override
    @Fluent
    public TestModel setHash(String hash) {
        someStringOne = hash;

        return this;
    }

    @Override
    @Fluent
    public TestModel setRange(String range) {
        someStringTwo = range;

        return this;
    }

    @Override
    public String getEtag() {
        return etag;
    }

    @Fluent
    @Override
    public TestModel setEtag(String etag) {
        this.etag = etag;

        return this;
    }

    @Override
    public String generateEtagKeyIdentifier() {
        return getSomeStringOne() != null && getSomeStringTwo() != null ?
                "data_api_testModel_etag_" + getSomeStringOne() + "_" + getSomeStringTwo() :
                "NoTestModelEtag";
    }

    @Override
    public TestModel setModifiables(Model newObject) {
        return this;
    }

    @Override
    public TestModel sanitize() {
        return this;
    }

    @Override
    public List<ValidationError> validateCreate() {
        return Collections.emptyList();
    }

    @Override
    public List<ValidationError> validateUpdate() {
        return Collections.emptyList();
    }

    @Override
    public Date getCreatedAt() {
        return createdAt != null ? createdAt : new Date();
    }

    @Override
    @Fluent
    public TestModel setCreatedAt(Date date) {
        createdAt = date;

        return this;
    }

    @Override
    public Date getUpdatedAt() {
        return updatedAt != null ? updatedAt : new Date();
    }

    @Override
    @Fluent
    public TestModel setUpdatedAt(Date date) {
        updatedAt = date;

        return this;
    }

    @Override
    @Fluent
    public TestModel setInitialValues(Model record) {
        return this;
    }

    @Override
    public JsonObject toJsonFormat(@Nonnull String[] projections) {
        return new JsonObject(Json.encode(this));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TestModel testModel = (TestModel) o;

        return Objects.equals(getSomeStringOne(), testModel.getSomeStringOne()) &&
                Objects.equals(getSomeStringTwo(), testModel.getSomeStringTwo()) &&
                Objects.equals(getSomeStringThree(), testModel.getSomeStringThree()) &&
                Objects.equals(getSomeStringFour(), testModel.getSomeStringFour()) &&
                Objects.equals(getSomeDate(), testModel.getSomeDate()) &&
                Objects.equals(getSomeDateTwo(), testModel.getSomeDateTwo()) &&
                Objects.equals(getSomeLong(), testModel.getSomeLong()) &&
                Objects.equals(getSomeLongTwo(), testModel.getSomeLongTwo()) &&
                Objects.equals(getSomeInteger(), testModel.getSomeInteger()) &&
                Objects.equals(getSomeIntegerTwo(), testModel.getSomeIntegerTwo()) &&
                Objects.equals(getSomeBoolean(), testModel.getSomeBoolean()) &&
                Objects.equals(getSomeBooleanTwo(), testModel.getSomeBooleanTwo()) &&
                Objects.equals(getDocuments(), testModel.getDocuments()) &&
                Objects.equals(getCreatedAt(), testModel.getCreatedAt()) &&
                Objects.equals(getUpdatedAt(), testModel.getUpdatedAt()) &&
                Objects.equals(getVersion(), testModel.getVersion());
    }

    @Override
    public int hashCode() {
        return Objects.hash(someStringOne, someStringTwo, someStringThree, someStringFour, someDate, someDateTwo,
                someLong, someLongTwo, someInteger, someIntegerTwo, someBoolean, someBooleanTwo, documents, createdAt,
                updatedAt, version);
    }
}
```

TestDocument:

```java
@DynamoDBDocument
@DataObject(generateConverter = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TestDocument implements ETagable {
    private String etag;
    private String someStringOne;
    private String someStringTwo;
    private String someStringThree;
    private String someStringFour;
    private Long version;

    public TestDocument() {

    }

    public TestDocument(JsonObject jsonObject) {
        fromJson(jsonObject, this);
    }

    public JsonObject toJson() {
        return JsonObject.mapFrom(this);
    }

    public String getSomeStringOne() {
        return someStringOne;
    }

    public void setSomeStringOne(String someStringOne) {
        this.someStringOne = someStringOne;
    }

    public String getSomeStringTwo() {
        return someStringTwo;
    }

    public void setSomeStringTwo(String someStringTwo) {
        this.someStringTwo = someStringTwo;
    }

    public String getSomeStringThree() {
        return someStringThree;
    }

    public void setSomeStringThree(String someStringThree) {
        this.someStringThree = someStringThree;
    }

    public String getSomeStringFour() {
        return someStringFour;
    }

    public void setSomeStringFour(String someStringFour) {
        this.someStringFour = someStringFour;
    }

    @DynamoDBVersionAttribute
    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    @Override
    public String getEtag() {
        return etag;
    }

    @Override
    public TestDocument setEtag(String etag) {
        this.etag = etag;

        return this;
    }

    @Override
    public String generateEtagKeyIdentifier() {
        return getSomeStringOne() != null ? "data_api_testDocument_etag_" + getSomeStringOne() : "NoDocumentTag";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TestDocument that = (TestDocument) o;

        return Objects.equals(getSomeStringOne(), that.getSomeStringOne()) &&
                Objects.equals(getSomeStringTwo(), that.getSomeStringTwo()) &&
                Objects.equals(getSomeStringThree(), that.getSomeStringThree()) &&
                Objects.equals(getSomeStringFour(), that.getSomeStringFour()) &&
                Objects.equals(getVersion(), that.getVersion());
    }

    @Override
    public int hashCode() {
        return Objects.hash(someStringOne, someStringTwo, someStringThree, someStringFour, version);
    }
}
```

#### 1.2.1.2 ImageUploader

### ImageUploader Interface

This interface provides a helper for converting images to JPG with 1.0 compression and storing it to a specific S3Link class, i.e. storing it in a S3 bucket.

File upload has three options:

- From filesystem file
- From Vert.x web FileUpload
- From external url

```java
default void doUpload(Vertx vertx, File file, Supplier<S3Link> s3LinkSupplier, Future<Boolean> fut) { ... }
default void doUpload(Vertx vertx, FileUpload file, Supplier<S3Link> s3LinkSupplier, Future<Boolean> fut) { ... }
default void doUpload(Vertx vertx, String url, Supplier<S3Link> s3LinkSupplier, Future<Boolean> fut) { ... }
```

#### 1.2.1.3 CachedContent

### CachedContent Interface

This is a helper interface that asks you to implement these two methods:

```java
S3Link getContentLocation();
void setContentLocation(S3Link s3Link);
```

***

At that point you can call this method on those objects to store any content accessible by url to the supplied contentlocation S3Link.

```java
default void storeContent(Vertx vertx, String urlToContent, String bucketPath, Handler<AsyncResult<Boolean>> resultHandler) { ... }
```

# 1.3 Querying

## 1.3.0 QueryPack

### QueryPack

The QueryPack class is used for doing queries on Repository objects.

It's primarily concerned with these fields: 

```java
...

    private String pageToken;
    private String requestEtag;
    private Queue<OrderByParameter> orderByQueue;
    private Map<String, List<FilterParameter>> params;
    private AggregateFunction aggregateFunction;
    private String[] projections;
    private String indexName;
    private Integer limit;

...
```

Which are set using its builder interface which can optionally accept a Class. This is useful for setting a unique "route" when you are not accepting a query from an API interface, e.g:

```java
QueryPack queryPack = QueryPack.builder(TYPE)
        .withPageToken(pageToken)
        .withRequestEtag(etag)
        .withOrderByQueue(orderByQueue)
        .withFilterParameters(params)
        .withAggregateFunction(aggregateFunction)
        .withProjections(projections)
        .withIndexName(indexName)
        .withLimit(limit)
        .build();
```

All QueryPack builder methods are optional, and you can also add FilterParameters directly with the following methods:

```java
public QueryPackBuilder addFilterParameter(String field, FilterParameter param) { ... }
public QueryPackBuilder addFilterParameters(String field, List<FilterParameter> parameters) { ... }
``` 
### Execution of Query Parameters

The QueryPack operates in order. So if you want to build complex boolean statements you should add the filterparameters in the order they would naturally follow.

For OrderByParameters they too are processed in insertion order.

### ETags and Caches

Every specific configuration of a querypack is unique and will produce different etags and caches.

***

### Vert.x Web Integration

The builder has a method called 

```java
public QueryPackBuilder withRoutingContext(RoutingContext routingContext) { ... }
```

This method will automatically set the pageToken based on an available pageToken param in the query and will also set the ETag from the request based on the "If-None-Match" HTTP Header.

## 1.3.1 FilterParameter

### FilterParameter

You can filter on fields with the following parameters:

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

***

The FilterParameter class defines a filtering specification for a field. It is easily constructed like this:

```java
FilterParameter.builder("someBoolean")
    .withEq("true")
    .build()
```

If you want to construct more complex combinations you can set the type of the specification as well. The default is AND. e.g:

someBoolean == true AND someOtherBoolean == false

***

This would set a filterparameter to OR, and OR on the next parameter in the list supplied to a QueryPack.

```java
FilterParameter.builder("someBoolean")
    .withEq("true")
    .withType("or")
    .build()
```

## 1.3.2 OrderByParameter

### OrderByParameter

This class is used to define ordering on a field of a model. Available options are:

* field (Field to order by)
* direction ("asc" or "desc", DESC is default)

An OrderByParameter is defined easily like this:

```java
OrderByParameter.builder()
    .withField("someLong")
    .withDirection("asc")
    .build()
```

Direction is DESC by default.

## 1.3.3 AggregateFunction

### AggregateFunction

The AggregateFunction class is used to define aggregation on a model.

Available functions are the following:

- MIN
- MAX
- AVG
- SUM
- COUNT

It also supports grouping, normal and ranged. It supports grouping three levels deep.

***

Constructing the AggregateFunction follows the same builder interface as other parameters:

```java
AggregateFunction aggregateFunction = AggregateFunction.builder()
        .withAggregateFunction(MAX)
        .withField("someLong")
        .build();
```

***

You can either set groupings explicitly

```java
AggregateFunction aggregateFunction = AggregateFunction.builder()
        .withAggregateFunction(MAX)
        .withField("someLong")
        .withGroupBy(Collections.singletonList(GroupingConfiguration.builder()
                .withGroupBy("someLong")
                .withGroupByUnit("INTEGER")
                .withGroupByRange(10000)
                .build()))
        .build();
```
, or add them one by one.

```java
AggregateFunction aggregateFunction = AggregateFunction.builder()
        .withAggregateFunction(MAX)
        .withField("someLong")
        .addGroupBy(GroupingConfiguration.builder()
               .withGroupBy("someLong")
               .withGroupByUnit("INTEGER")
               .withGroupByRange(10000)
               .build())
        .build();
```

## 1.3.4 Projection

### Projection

Projection is very simple. You pass in a String array containing the fields you want, and those fields will be returned to you. Exact response might differ between implementations.