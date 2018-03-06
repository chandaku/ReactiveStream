package com.stream;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import reactor.core.publisher.ConnectableFlux;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

public class StreamTest {
    public static void main(String[] args) {
        //coldStream();
        hotStream();

    }

    private static void hotStream() {
        //connectableFlux();
        conncurrentFlux();

    }

    private static void conncurrentFlux() {
        List<Integer> elements = new ArrayList<>();
        Flux.just(1, 2, 3, 4)
                .log()
                .map(i -> i * 2)
                .subscribeOn(Schedulers.parallel())
                .subscribe(elements::add);
    }

    private static void connectableFlux() {
        ConnectableFlux<Object> publish = Flux.create(fluxSink -> {
            while(true) {
                fluxSink.next(System.currentTimeMillis());
            }
        }).sample(Duration.ofSeconds(2))
                .publish();

        publish.subscribe(System.out::println);
        //publish.subscribe(System.out::println);
        publish.connect();
    }

    private static void coldStream() {
        flux();
        backPressure();
        mapStream();
        combineStream();
    }


    public static void flux() {
        List<Integer> elements = new ArrayList<>();
        Queue<Integer> inputs = new LinkedList<>();
        inputs.addAll(Arrays.asList(1, 2, 3, 4));
        Flux.just(inputs.peek())
                .log()
                .subscribe(elements::add);
    }

    public static void backPressure() {
        List<Integer> elements = new ArrayList<>();
        Flux.just(1, 2, 3, 4, 5, 6, 7, 8, 9, 0)
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
                    public void onError(Throwable t) {
                    }

                    @Override
                    public void onComplete() {
                    }
                });
    }


    private static void mapStream() {
        List<Integer> elements = new ArrayList<>();
        Flux.just(1, 2, 3, 4)
                .log()
                .map(i -> i * 2)
                .subscribe(elements::add);
    }

    public static void combineStream() {
        List<Integer> elements = new ArrayList<>();

        Flux.just(1, 2, 3, 4)
                .log()
                .map(i -> i * 2)
                .zipWith(Flux.range(0, Integer.MAX_VALUE),
                        (two, one) -> String.format("First Flux: %d, Second Flux: %d", one, two));
              //  .subscribe(elements::add);
    }
}