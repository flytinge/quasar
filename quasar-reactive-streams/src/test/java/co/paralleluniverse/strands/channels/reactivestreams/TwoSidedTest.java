/*
 * Copyright (c) 2013-2015, Parallel Universe Software Co. All rights reserved.
 * 
 * This program and the accompanying materials are dual-licensed under
 * either the terms of the Eclipse Public License v1.0 as published by
 * the Eclipse Foundation
 *  
 *   or (per the licensee's choosing)
 *  
 * under the terms of the GNU Lesser General Public License version 3.0
 * as published by the Free Software Foundation.
 */
package co.paralleluniverse.strands.channels.reactivestreams;

import co.paralleluniverse.fibers.Fiber;
import co.paralleluniverse.fibers.SuspendExecution;
import co.paralleluniverse.strands.Strand;
import co.paralleluniverse.strands.SuspendableRunnable;
import co.paralleluniverse.strands.channels.Channel;
import co.paralleluniverse.strands.channels.Channels;
import co.paralleluniverse.strands.channels.Channels.OverflowPolicy;
import co.paralleluniverse.strands.channels.ReceivePort;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import org.reactivestreams.Publisher;
import org.testng.annotations.*;
import static org.testng.Assert.*;

public class TwoSidedTest {
    private static final long ELEMENTS = 10_000;
    private final int buffer;
    private final OverflowPolicy overflowPolicy;
    private final boolean batch;

    @Factory(dataProvider = "params")
    public TwoSidedTest(int buffer, OverflowPolicy overflowPolicy, boolean batch) {
        this.buffer = buffer;
        this.overflowPolicy = overflowPolicy;
        this.batch = batch;
    }

    @DataProvider(name = "params")
    public static Object[][] data() {
        return new Object[][]{
            {5, OverflowPolicy.THROW, false},
            {5, OverflowPolicy.THROW, true},
            {5, OverflowPolicy.BLOCK, false},
            {5, OverflowPolicy.BLOCK, true},
            {-1, OverflowPolicy.THROW, false},
            {-1, OverflowPolicy.THROW, true},
            // {5, OverflowPolicy.DISPLACE, false},
            // {5, OverflowPolicy.DISPLACE, true},
            {1, OverflowPolicy.BLOCK, false},
            {1, OverflowPolicy.BLOCK, true}
        };
    }

    @Test
    public void twoSidedTest() throws Exception {
        final Channel<Integer> publisherChannel = Channels.newChannel(random() ? 0 : 5, OverflowPolicy.BLOCK);
        final Strand publisherStrand = new Fiber<Void>(new SuspendableRunnable() {
            @Override
            public void run() throws SuspendExecution, InterruptedException {
                for (long i = 0; i < ELEMENTS; i++)
                    publisherChannel.send((int) (i % 1000));
                
                publisherChannel.close();
            }
        }).start();
        final Publisher publisher = ReactiveStreams.toPublisher(publisherChannel);

        final ReceivePort<Integer> subscriberChannel = ReactiveStreams.subscribe(buffer, overflowPolicy, batch, publisher);
        final Strand subscriberStrand = new Fiber<Void>(new SuspendableRunnable() {
            @Override
            public void run() throws SuspendExecution, InterruptedException {
                long count = 0;
                for (;;) {
                    Integer x = subscriberChannel.receive();
                    if (x == null)
                        break;
                    
                    assertEquals(count % 1000, x.longValue());
                    count++;
                }
                subscriberChannel.close();

                assertEquals(ELEMENTS, count);
            }
        }).start();

        subscriberStrand.join(5, TimeUnit.SECONDS);
        publisherStrand.join(5, TimeUnit.SECONDS);
    }
    
    private boolean random() {
        return ThreadLocalRandom.current().nextBoolean();
    }
}
