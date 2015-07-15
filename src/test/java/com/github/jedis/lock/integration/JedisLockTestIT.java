package com.github.jedis.lock.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.BeforeClass;
import org.junit.Test;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.Protocol;

import com.github.jedis.lock.JedisLock;

/**
 * Integration test - please see HostAndPortUtil to set where your server is
 * 
 * To launch of course: mvn failsafe:integration-test  passing the necessary JVM arguments, i.e.: 
 * mvn -Djedis.host=localhost -Djedis.auth=foobared failsafe:integration-test  
 * 
 */
public class JedisLockTestIT {

    private static final String JEDIS_HOST = System.getProperty("jedis.host", "localhost");
    private static final Integer JEDIS_PORT = Integer.getInteger("jedis.port", Protocol.DEFAULT_PORT);
    private static final String JEDIS_AUTH = System.getProperty("jedis.auth");

    @BeforeClass
    public static void setup() {

        String hostinfo = String.format("endpoint=%s:%d, auth=%s", JEDIS_HOST, JEDIS_PORT, JEDIS_AUTH);

        System.out.println("Using redis at " + hostinfo);
        Jedis jedis = new Jedis(JEDIS_HOST, JEDIS_PORT);
        try {
            connect(jedis);
        } catch (Exception ex) {
            System.err.println("Unable to connect to Jedis - " + hostinfo);
            fail("Unable to connect to Jedis - " + hostinfo);
        } finally {
            disconnect(jedis);
        }
    }

    @Test
    public void testAcquire() throws InterruptedException {
        Jedis jedis = connect();

        JedisLock lock = new JedisLock(jedis, "testlock2");
        assertTrue(lock.acquire());

        JedisLock lock2 = new JedisLock(jedis, "testlock2", 1000);
        assertFalse(lock2.acquire());

        lock.release();

        lock2 = new JedisLock(jedis, "testlock2", 1000);
        assertTrue(lock2.acquire());
        lock2.release();
    }

    @Test
    public void testRenew() throws InterruptedException {
        Jedis jedis = connect();

        JedisLock lock = new JedisLock(jedis, "testlock2");
        assertTrue(lock.acquire());

        Thread.sleep(2000l);

        assertTrue(lock.renew());

        lock.release();

        JedisLock lock2 = new JedisLock(jedis, "testlock2", 1000);
        assertTrue(lock2.acquire());
        lock2.release();
    }

    @Test
    public void testConcurrency() throws InterruptedException {
        final int count = 10;
        
        ConcurrentLocker[] lockers = new ConcurrentLocker[]{
                new ConcurrentLocker(count), 
                new ConcurrentLocker(count), 
                new ConcurrentLocker(count)};

        for (ConcurrentLocker locker : lockers) {
            locker.start();
        }

        for (ConcurrentLocker locker : lockers) {
            locker.join();
        }

        for (ConcurrentLocker locker : lockers) {
            assertEquals(count, locker.count());
        }
    }

    private class ConcurrentLocker extends Thread {

        private final int times;
        private int counter;
        
        public ConcurrentLocker(int times) {
            this.times = times;
            this.counter = 0;
        }
        
        public void run() {
            Jedis jedis = connect();
            try {
                for (int i = 0; i < times; i++) {
                    JedisLock lock = new JedisLock(jedis, "testlock", 15000, 200);
                    try {
                        if (lock.acquire()) {
                            counter++;
                            Thread.sleep(250);
                            lock.release();
                        }
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            } finally {
                disconnect(jedis);
            }
        }
        
        public int count() {
            return counter;
        }
    };

    
    private Jedis connect() {
        Jedis jedis = new Jedis(JEDIS_HOST, JEDIS_PORT);
        connect(jedis);
        return jedis;
    }

    private static void connect(Jedis jedis) {
        jedis.connect();
        jedis.auth(JEDIS_AUTH);
    }

    private static void disconnect(Jedis jedis) {
        try {
            jedis.disconnect();
        } catch (Throwable ignore) {
        }
    }

}
