package com.github.jedis.lock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import redis.clients.jedis.Jedis;

import com.github.jedis.lock.HostAndPortUtil.HostAndPort;

public class JedisLockTest {

	private static HostAndPort redis1 = HostAndPortUtil.getRedisServers().get(0);

	@Test
	public void testAcquire() throws InterruptedException {
		Jedis jedis = new Jedis(redis1.host, redis1.port);
        jedis.connect();
        jedis.auth("foobared");

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
	public void testConcurrency() throws InterruptedException {
		final AtomicInteger t0Acquired = new AtomicInteger();
		final AtomicInteger t1Acquired = new AtomicInteger();

		final int count = 10;
		
		final Thread t0 = new Thread(new Runnable() {

			public void run() {
				Jedis jedis = new Jedis(redis1.host, redis1.port);
		        jedis.connect();
		        jedis.auth("foobared");

				for (int i = 0; i < count; i++) {
					JedisLock lock = new JedisLock(jedis, "testlock", 15000, 200);
					try {
						if (lock.acquire()) {
							t0Acquired.incrementAndGet();
							Thread.sleep(250);
							lock.release();
						}
						Thread.sleep(100);
					} catch (InterruptedException e) {
						return;
					}
				}
			}
		});

		Thread t1 = new Thread(new Runnable() {

			public void run() {
				Jedis jedis = new Jedis(redis1.host, redis1.port);
		        jedis.connect();
		        jedis.auth("foobared");

				for (int i = 0; i < count; i++) {
					JedisLock lock = new JedisLock(jedis, "testlock", 15000, 200);
					try {
						if (lock.acquire()) {
							t1Acquired.incrementAndGet();
							Thread.sleep(100);
							lock.release();
						}
						Thread.sleep(100);
					} catch (InterruptedException e) {
						return;
					}
				}
			}
		});

		t0.start();
		t1.start();

		t0.join();
		t1.join();

		assertEquals(count, t0Acquired.get());
		assertEquals(count, t1Acquired.get());
	}
}
