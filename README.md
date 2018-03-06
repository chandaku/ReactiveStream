# ReactiveStream

##1. Introduction
Reactor Core is a Java 8 library which implements the reactive programming model. It’s built on top of the Reactive Streams Specification, a standard for building reactive applications.

From the background of non-reactive Java development, going reactive can be quite a steep learning curve. This becomes more challenging when comparing it to the Java 8 Stream API, as they could be mistaken for being the same high-level abstractions.

In this article, we’ll attempt to demystify this paradigm. We’ll take small steps through Reactor until we’ve built a picture of how to compose reactive code, laying the foundation for more advanced articles to come in a later series.

##2. Reactive Streams Specification
Before we look at Reactor, we should look at the Reactive Streams Specification. This is what Reactor implements, and it lays the groundwork for the library.

Essentially, Reactive Streams is a specification for asynchronous stream processing.

In other words, a system where lots of events are being produced and consumed asynchronously. Think about a stream of thousands of stock updates per second coming into a financial application, and for it to have to respond to those updates in a timely manner.

One of the main goals of this is to address the problem of back pressure. If we have a producer which is emitting events to a consumer faster than it can process them, then eventually the consumer will be overwhelmed with events, running out of system resources. Backpressure means that our consumer should be able to tell the producer how much data to send in order to prevent this, and this is what is laid out in the specification.

##3. Maven Dependencies
Before we get started, let’s add our Maven dependencies:

<dependency>
    <groupId>io.projectreactor</groupId>
    <artifactId>reactor-core</artifactId>
    <version>3.0.5.RELEASE</version>
</dependency>
 
<dependency> 
    <groupId>ch.qos.logback</groupId> 
    <artifactId>logback-classic</artifactId> 
    <version>1.1.3</version> 
</dependency>
We’re also adding Logback as a dependency. This is because we’ll be logging the output of Reactor in order to better understand the flow of data.

##4. Producing a Stream of Data
In order for an application to be reactive, the first thing it must be able to do is to produce a stream of data. This could be something like the stock update example that we gave earlier. Without this data, we wouldn’t have anything to react to, which is why this is a logical first step. Reactive Core gives us two data types that enable us to do this.

###4.1. Flux
The first way of doing this is with a Flux.  It’s a stream which can emit 0..n elements. Let’s try creating a simple one:

Flux<String> just = Flux.just("1", "2", "3");
In this case, we have a static stream of three elements.

###4.2. Mono
The second way of doing this is with a Mono, which is a stream of 0..1 elements. Let’s try instantiating one:

Mono<String> just = Mono.just("foo");
This looks and behaves almost exactly the same as the Flux, only this time we are limited to no more than one element.
###4.3. Why Not Just Flux?
Before experimenting further, it’s worth highlighting why we have these two data types.

First, it should be noted that both a Flux and Mono are implementations of the Reactive Streams Publisher interface. Both classes are compliant with the specification, and we could use this interface in their place:

Publisher<String> just = Mono.just("foo");
But really, knowing this cardinality is useful. This is because a few operations only make sense for one of the two types, and because it can be more expressive (imagine findOne() in a repository).

##5. Subscribing to a Stream
Now we have a high-level overview of how to produce a stream of data, we need to subscribe to it in order for it to emit the elements.

### 5.1. Collecting Elements
Let’s use the subscribe() method to collect all the elements in a stream:


List<Integer> elements = new ArrayList<>();
 
Flux.just(1, 2, 3, 4)
  .log()
  .subscribe(elements::add);
 
assertThat(elements).containsExactly(1, 2, 3, 4);
The data won’t start flowing until we subscribe. Notice that we have added some logging as well, this will be helpful when we look at what’s happening behind the scenes.

### 5.2. The Flow of Elements
With logging in place, we can use it to visualize how the data is flowing through our stream:


 [main] INFO  reactor.Flux.Array.1 - | onSubscribe([Synchronous Fuseable] FluxArray.ArraySubscription)
 [main] INFO  reactor.Flux.Array.1 - | request(unbounded)
 [main] INFO  reactor.Flux.Array.1 - | onNext(1)
 [main] INFO  reactor.Flux.Array.1 - | onNext(2)
 [main] INFO  reactor.Flux.Array.1 - | onNext(3)
 [main] INFO  reactor.Flux.Array.1 - | onNext(4)
 [main] INFO  reactor.Flux.Array.1 - | onComplete()
First of all, everything is running on the main thread. Let’s not go into any details about this, as we’ll be taking a further look at concurrency later on in this article. It does make things simple, though, as we can deal with everything in order.

Now let’s go through the sequence that we have logged one by one:

onSubscribe() – This is called when we subscribe to our stream
request(unbounded) – When we call subscribe, behind the scenes we are creating a Subscription. This subscription requests elements from the stream. In this case, it defaults to unbounded, meaning it requests every single element available
onNext() – This is called on every single element
onComplete() – This is called last, after receiving the last element. There’s actually a onError() as well, which would be called if there is an exception, but in this case, there isn’t
This is the flow laid out in the Subscriber interface as part of the Reactive Streams Specification, and in reality, that’s what’s been instantiated behind the scenes in our call to onSubscribe(). It’s a useful method, but to better understand what’s happening let’s provide a Subscriber interface directly:


Flux.just(1, 2, 3, 4)
  .log()
  .subscribe(new Subscriber<Integer>() {
    @Override
    public void onSubscribe(Subscription s) {
      s.request(Long.MAX_VALUE);
    }
 
    @Override
    public void onNext(Integer integer) {
      elements.add(integer);
    }
 
    @Override
    public void onError(Throwable t) {}
 
    @Override
    public void onComplete() {}
});
We can see that each possible stage in the above flow maps to a method in the Subscriber implementation. It just happens that the Flux has provided us with a helper method to reduce this verbosity.

###5.3. Comparison to Java 8 Streams
It still might appear that we have something synonymous to a Java 8 Stream doing collect:

List<Integer> collected = Stream.of(1, 2, 3, 4)
  .collect(toList());
Only we don’t.

The core difference is that Reactive is a push model, whereas the Java 8 Streams are a pull model. In reactive approach. events are pushed to the subscribers as they come in. 

The next thing to notice is a Streams terminal operator is just that, terminal, pulling all the data and returning a result. With Reactive we could have an infinite stream coming in from an external resource, with multiple subscribers attached and removed on an ad hoc basis. We can also do things like combine streams, throttle streams and apply backpressure, which we will cover next.

##6. Backpressure
The next thing we should consider is backpressure. In our example, the subscriber is telling the producer to push every single element at once. This could end up becoming overwhelming for the subscriber, consuming all of its resources.

Backpressure is when a downstream can tell an upstream to send it fewer data in order to prevent it from being overwhelmed.

We can modify our Subscriber implementation to apply backpressure. Let’s tell the upstream to only send two elements at a time by using request():


Flux.just(1, 2, 3, 4)
  .log()
  .subscribe(new Subscriber<Integer>() {
    private Subscription s;
    int onNextAmount;
 
    @Override
    public void onSubscribe(Subscription s) {
        this.s = s;
        s.request(2);
    }
 
    @Override
    public void onNext(Integer integer) {
        elements.add(integer);
        onNextAmount++;
        if (onNextAmount % 2 == 0) {
            s.request(2);
        }
    }
 
    @Override
    public void onError(Throwable t) {}
 
    @Override
    public void onComplete() {}
});
Now if we run our code again, we’ll see the request(2) is called, followed by two onNext() calls, then request(2) again.


 [main] INFO  reactor.Flux.Array.1 - | onSubscribe([Synchronous Fuseable] FluxArray.ArraySubscription)
 [main] INFO  reactor.Flux.Array.1 - | request(2)
 [main] INFO  reactor.Flux.Array.1 - | onNext(1)
 [main] INFO  reactor.Flux.Array.1 - | onNext(2)
 [main] INFO  reactor.Flux.Array.1 - | request(2)
 [main] INFO  reactor.Flux.Array.1 - | onNext(3)
 [main] INFO  reactor.Flux.Array.1 - | onNext(4)
 [main] INFO  reactor.Flux.Array.1 - | request(2)
 [main] INFO  reactor.Flux.Array.1 - | onComplete()
Essentially, this is reactive pull backpressure. We are requesting the upstream to only push a certain amount of elements, and only when we are ready. If we imagine we were being streamed tweets from twitter, it would then be up to the upstream to decide what to do. If tweets were coming in but there are no requests from the downstream, then the upstream could drop items, store them in a buffer, or some other strategy.

##7. Operating on a Stream
We can also perform operations on the data in our stream, responding to events as we see fit.

###7.1. Mapping Data in a Stream
A simple operation that we can perform is applying a transformation. In this case, let’s just double all the numbers in our stream:


Flux.just(1, 2, 3, 4)
  .log()
  .map(i -> i * 2)
  .subscribe(elements::add);
map() will be applied when onNext() is called.

###7.2. Combining two Streams
We can then make things more interesting by combining another stream with this one. Let’s try this by using zip() function:


Flux.just(1, 2, 3, 4)
  .log()
  .map(i -> i * 2)
  .zipWith(Flux.range(0, Integer.MAX_VALUE), 
    (two, one) -> String.format("First Flux: %d, Second Flux: %d", one, two))
  .subscribe(elements::add);
 
assertThat(elements).containsExactly(
  "First Flux: 0, Second Flux: 2",
  "First Flux: 1, Second Flux: 4",
  "First Flux: 2, Second Flux: 6",
  "First Flux: 3, Second Flux: 8");
Here, we are creating another Flux which keeps incrementing by one and streaming it together with our original one. We can see how these work together by inspecting the logs:


 [main] INFO  reactor.Flux.Array.1 - | onSubscribe([Synchronous Fuseable] FluxArray.ArraySubscription)
 [main] INFO  reactor.Flux.Array.1 - | onNext(1)
 [main] INFO  reactor.Flux.Range.2 - | onSubscribe([Synchronous Fuseable] FluxRange.RangeSubscription)
 [main] INFO  reactor.Flux.Range.2 - | onNext(0)
 [main] INFO  reactor.Flux.Array.1 - | onNext(2)
 [main] INFO  reactor.Flux.Range.2 - | onNext(1)
 [main] INFO  reactor.Flux.Array.1 - | onNext(3)
 [main] INFO  reactor.Flux.Range.2 - | onNext(2)
 [main] INFO  reactor.Flux.Array.1 - | onNext(4)
 [main] INFO  reactor.Flux.Range.2 - | onNext(3)
 [main] INFO  reactor.Flux.Array.1 - | onComplete()
 [main] INFO  reactor.Flux.Array.1 - | cancel()
 [main] INFO  reactor.Flux.Range.2 - | cancel()
Note how we now have one subscription per Flux. The onNext() calls are also alternated, so the index of each element in the stream will match when we apply the zip() function.

##8. Hot Streams
Currently, we’ve focused primarily on cold streams. These are static, fixed length streams which are easy to deal with. A more realistic use case for reactive might be something that happens infinitely. For example, we could have a stream of mouse movements which constantly needs to be reacted to or a twitter feed. These types of streams are called hot streams, as they are always running and can be subscribed to at any point in time, missing the start of the data.

### 8.1. Creating a ConnectableFlux
One way to create a hot stream is by converting a cold stream into one. Let’s create a Flux that lasts forever, outputting the results to the console, which would simulate an infinite stream of data coming from an external resource:


ConnectableFlux<Object> publish = Flux.create(fluxSink -> {
    while(true) {
        fluxSink.next(System.currentTimeMillis());
    }
})
  .publish();
By calling publish() we are given a ConnectableFlux. This means that calling subscribe() won’t cause it start emitting, allowing us to add multiple subscriptions:


publish.subscribe(System.out::println);        
publish.subscribe(System.out::println);
If we try running this code, nothing will happen. It’s not until we call connect(), that the Flux will start emitting. It doesn’t matter whether we are subscribing or not.

### 8.2. Throttling
If we run our code, our console will be overwhelmed with logging. This is simulating a situation where too much data is being passed to our consumers. Let’s try getting around this with throttling:


ConnectableFlux<Object> publish = Flux.create(fluxSink -> {
    while(true) {
        fluxSink.next(System.currentTimeMillis());
    }
})
  .sample(ofSeconds(2))
  .publish();
Here, we’ve introduced a sample() method with an interval of two seconds. Now values will only be pushed to our subscriber every two seconds, meaning the console will be a lot less hectic.

Of course, there’s multiple strategies to reduce the amount of data sent downstream, such as windowing and buffering, but they will be left out of scope for this article.

## 9. Concurrency
All of our above examples have currently run on the main thread. However, we can control which thread our code runs on if we want. The Scheduler interface provides an abstraction around asynchronous code, for which many implementations are provided for us. Let’s try subscribing to a different thread to main:


Flux.just(1, 2, 3, 4)
  .log()
  .map(i -> i * 2)
  .subscribeOn(Schedulers.parallel())
  .subscribe(elements::add);
The Parallel scheduler will cause our subscription to be run on a different thread, which we can prove by looking at the logs:


 [parallel-1] INFO  reactor.Flux.Array.1 - | onComplete()
Concurrency get’s more interesting that this, and it will be worth us exploring it in another article.