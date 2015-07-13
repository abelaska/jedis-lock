package com.github.jedis.lock;

import redis.clients.jedis.Jedis;

/**
 * Redis distributed lock implementation
 * (fork by  Bruno Bossola <bbossola@gmail.com>)
 * 
 * @author Alois Belaska <alois.belaska@gmail.com>
 */
public class JedisLock {

    private static final int ONE_SECOND = 1000;

    public static final int DEFAULT_EXPIRY_TIME_MILLIS = Integer.getInteger("com.github.jedis.lock.expiry.millis", 60 * ONE_SECOND);
    public static final int DEFAULT_ACQUIRE_TIMEOUT_MILLIS = Integer.getInteger("com.github.jedis.lock.acquiry.millis", 10 * ONE_SECOND);
    public static final int DEFAULT_ACQUIRY_RESOLUTION_MILLIS = Integer.getInteger("com.github.jedis.lock.acquiry.resolution.millis", 100);

    private final Jedis jedis;

    private final String lockKeyPath;

    private final int lockExpiryInMillis;
    private final int acquiryTimeoutInMillis;

    private Long timoutExpiryTime = null;

    /**
     * Detailed constructor with default acquire timeout 10000 msecs and lock
     * expiration of 60000 msecs.
     * 
     * @param jedis
     * @param lockKey
     *            lock key (ex. account:1, ...)
     */
    public JedisLock(Jedis jedis, String lockKey) {
        this(jedis, lockKey, DEFAULT_ACQUIRE_TIMEOUT_MILLIS, DEFAULT_EXPIRY_TIME_MILLIS);
    }

    /**
     * Detailed constructor with default lock expiration of 60000 msecs.
     * 
     * @param jedis
     * @param lockKey
     *            lock key (ex. account:1, ...)
     * @param acquireTimeoutMillis
     *            acquire timeout in miliseconds (default: 10000 msecs)
     */
    public JedisLock(Jedis jedis, String lockKey, int acquireTimeoutMillis) {
        this(jedis, lockKey, acquireTimeoutMillis, DEFAULT_EXPIRY_TIME_MILLIS);
    }

    /**
     * Detailed constructor.
     * 
     * @param jedis
     * @param lockKey
     *            lock key (ex. account:1, ...)
     * @param acquireTimeoutMillis
     *            acquire timeout in miliseconds (default: 10000 msecs)
     * @param expiryTimeMillis
     *            lock expiration in miliseconds (default: 60000 msecs)
     */
    public JedisLock(Jedis jedis, String lockKey, int acquireTimeoutMillis, int expiryTimeMillis) {
        this.jedis = jedis;
        this.lockKeyPath = lockKey;
        this.acquiryTimeoutInMillis = acquireTimeoutMillis;
        this.lockExpiryInMillis = expiryTimeMillis;
    }

    /**
     * @return lock key path
     */
    public String getLockKeyPath() {
        return lockKeyPath;
    }

    /**
     * Acquire lock.
     * 
     * @param jedis
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
     * @return true if lock is acquired, false acquire timeouted
     * @throws InterruptedException
     *             in case of thread interruption
     */
    protected synchronized boolean acquire(Jedis jedis) throws InterruptedException {
        int timeout = acquiryTimeoutInMillis;
        while (timeout >= 0) {
            long expires = System.currentTimeMillis() + lockExpiryInMillis + 1;
            String expiresStr = String.valueOf(expires);

            if (jedis.setnx(lockKeyPath, expiresStr) == 1) {
                // lock acquired
                timoutExpiryTime = expires;
                return true;
            }

            String currentValueStr = jedis.get(lockKeyPath);
            if (currentValueStr != null && Long.parseLong(currentValueStr) < System.currentTimeMillis()) {
                // lock is expired
                String oldValueStr = jedis.getSet(lockKeyPath, expiresStr);
                if (oldValueStr != null && oldValueStr.equals(currentValueStr)) {
                    // lock acquired
                    timoutExpiryTime = expires;
                    return true;
                }
            }

            timeout -= DEFAULT_ACQUIRY_RESOLUTION_MILLIS;
            Thread.sleep(DEFAULT_ACQUIRY_RESOLUTION_MILLIS);
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
    protected synchronized void release(Jedis jedis) {
        if (isLocked()) {
            jedis.del(lockKeyPath);
            timoutExpiryTime = null;
        }
    }

    /**
     * Check if owns the lock
     * @return  true if lock owned
     */
    public synchronized boolean isLocked() {
        return timoutExpiryTime != null;
    }
    
    /**
     * Returns the expiry time of this lock
     * @return  the expiry time in millis (or null if not locked)
     */
    public synchronized long getExpiryTimeInMillis() {
        return timoutExpiryTime;
    }
}
