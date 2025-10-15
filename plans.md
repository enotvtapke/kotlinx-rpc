## Plans

### Tasks

* **Remote regular functions** | *#feature*  
  Now only methods of remote classes can be invoked remotely. I may be nice to add the opportunity to invoke every 
  function remotely, including lambdas. It may be possible by marking remote function with `@Remote`.
* **Garbage collection, remote instances lifespan** | *#feature*  
  Now when some remote class instance was created in the pool of the server, there is no way to delete it and free memory.
  I suggest adding several ways to clean memory. For example:
  * When connection with a client is interrupted, the server should delete all the client's objects. Probably after some period
    of time (like 30 seconds). I am not really sure how connections are handled now and generally in websocket (which is used in kotlin-rpc).
  * Add a new method in all remote classes that can be invoked on the client to remove a specific instance of the class
    from the server's pool. This can be implemented by requiring all inheriting some common interface. This interface may be 
    used to mark remote classes instead of @Remote annotation. In this case it should also contain context and rpcClient properties.
    This method can also be invoked in finalize method, but it can be a bad idea.
* **Remote objects, static lifespan, shared state** | *#feature*  
  Now all the remote instances on the server should be created explicitly. That can be inconvenient, for example, to 
  create DI container and config. It may be nice to have classes which instances are created on server by default and accessible 
  by all clients. These instances should never be garbage collected and will allow to share state between clients 
  (maybe it's better to forbid usage of such classes to implement shared state on the level of documentation). 
  Such classes are essentially objects from Kotlin. Thus, I suggest making possible mark Kotlin objects as remote
  for them to acquire described functionality.

  Now, to mimic this functionality, one can use a regular object and remote class to access methods and properties of this object.
* **Public fields in remote classes accessible only from the remote class context** | *#feature*   
  Now remote classes cannot have public or internal fields because it makes their state not private. With the introduction
  of context it is now possible to make remote class public but only accessible from the context of remote class. This way
  remote class fields will be "context private". 
* **When function with implicit return type has `Flow` type, IndexOutOfBoundsException exception is thrown** | *#bug*  
  This bug exists in unmodified kotlin-rpc. But even if it was fixed, another exception would be thrown, because when return 
  type is implicit I add suspend modifier 
  no matter this type is `Flow` or not. I do it because there is no way to check implicit
  return type in `FirStatusTransformerExtension`.

### Questions
* What to do when a remote class instance is returned from a remote method? Should I honestly serialize it or return 
  only id, which will be used to create stub on the client?
  ```kotlin
    @Remote(ServerClassContext::class)
    class TodoRepository {
        fun getAll(): List<TodoDto> = TODO()
    }
    
    @Remote(ServerClassContext::class)
    class TodoApi {
        fun repo(): TodoRepository = TodoRepository()
    }
  ```
    Answer:  
    I could use custom serializers deserializers for remote classes which would serialize remote classes only as ids. But it means
    that when I return a remote class created in its context, I should somehow add this created class to the pool of this context.