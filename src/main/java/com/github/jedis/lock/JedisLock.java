package com.github.jedis.lock;

import redis.clients.jedis.Jedis;

/**
 * Redis distributed lock implementation.
 * 
 * @author Alois Belaska <alois.belaska@gmail.com>
 */
public class JedisLock {

	private Jedis jedis;

	/**
	 * Lock key path.
	 */
	private String lockKey;

	/**
	 * Lock expiration in milliseconds.
	 */
	private int expireMsecs = 60 * 1000;

	/**
	 * Acquire timeout in milliseconds.
	 */
	private int timeoutMsecs = 10 * 1000;

	private boolean locked = false;

	/**
	 * Detailed constructor with default acquire timeout 10000 msecs and lock expiration of 60000 msecs.
	 * 
	 * @param jedis
	 * @param lockKey
	 *            lock key (ex. account:1, ...)
	 */
	public JedisLock(Jedis jedis, String lockKey) {
		this.jedis = jedis;
		this.lockKey = lockKey;
	}

	/**
	 * Detailed constructor with default lock expiration of 60000 msecs.
	 * 
	 * @param jedis
	 * @param lockKey
	 *            lock key (ex. account:1, ...)
	 * @param timeoutMsecs
	 *            acquire timeout in milliseconds (default: 10000 msecs)
	 */
	public JedisLock(Jedis jedis, String lockKey, int timeoutMsecs) {
		this(jedis, lockKey);
		this.timeoutMsecs = timeoutMsecs;
	}

	/**
	 * Detailed constructor.
	 * 
	 * @param jedis
	 * @param lockKey
	 *            lock key (ex. account:1, ...)
	 * @param timeoutMsecs
	 *            acquire timeout in milliseconds (default: 10000 msecs)
	 * @param expireMsecs
	 *            lock expiration in milliseconds (default: 60000 msecs)
	 */
	public JedisLock(Jedis jedis, String lockKey, int timeoutMsecs, int expireMsecs) {
		this(jedis, lockKey, timeoutMsecs);
		this.expireMsecs = expireMsecs;
	}

	/**
	 * Detailed constructor with default acquire timeout 10000 msecs and lock expiration of 60000 msecs.
	 * 
	 * @param lockKey
	 *            lock key (ex. account:1, ...)
	 */
	public JedisLock(String lockKey) {
		this(null, lockKey);
	}

	/**
	 * Detailed constructor with default lock expiration of 60000 msecs.
	 * 
	 * @param lockKey
	 *            lock key (ex. account:1, ...)
	 * @param timeoutMsecs
	 *            acquire timeout in miliseconds (default: 10000 msecs)
	 */
	public JedisLock(String lockKey, int timeoutMsecs) {
		this(null, lockKey, timeoutMsecs);
	}

	/**
	 * Detailed constructor.
	 * 
	 * @param lockKey
	 *            lock key (ex. account:1, ...)
	 * @param timeoutMsecs
	 *            acquire timeout in miliseconds (default: 10000 msecs)
	 * @param expireMsecs
	 *            lock expiration in miliseconds (default: 60000 msecs)
	 */
	public JedisLock(String lockKey, int timeoutMsecs, int expireMsecs) {
		this(null, lockKey, timeoutMsecs, expireMsecs);
	}

	/**
	 * @return lock key
	 */
	public String getLockKey() {
		return lockKey;
	}

	/**
	 * Acquire lock.
	 * 
	 * @return true if lock is acquired, false acquire timeouted
	 * @throws InterruptedException
	 *             in case of thread interruption
	 */
	public synchronized boolean acquire() throws InterruptedException {
		return acquire(jedis);
	}

	/**
	 * Acquire lock.
	 * 
	 * @param jedis
	 * @return true if lock is acquired, false acquire timed out
	 * @throws InterruptedException
	 *             in case of thread interruption
	 */
	public synchronized boolean acquire(Jedis jedis) throws InterruptedException {
		int timeout = timeoutMsecs;
		while (timeout >= 0) {
			long expires = System.currentTimeMillis() + expireMsecs + 1;
			String expiresStr = String.valueOf(expires);

			if (jedis.setnx(lockKey, expiresStr) == 1) {
				// lock acquired
				locked = true;
				return true;
			}

			String currentValueStr = jedis.get(lockKey);
			if (currentValueStr != null && Long.parseLong(currentValueStr) < System.currentTimeMillis()) {
				// lock is expired

				String oldValueStr = jedis.getSet(lockKey, expiresStr);
				if (oldValueStr != null && oldValueStr.equals(currentValueStr)) {
					// lock acquired
					locked = true;
					return true;
				}
			}

			timeout -= 100;
			Thread.sleep(100);
		}

		return false;
	}

	/**
	 * Acquired lock release.
	 */
	public synchronized void release() {
		release(jedis);
	}

	/**
	 * Acquired lock release.
	 */
	public synchronized void release(Jedis jedis) {
		if (locked) {
			jedis.del(lockKey);
			locked = false;
		}
	}
}
