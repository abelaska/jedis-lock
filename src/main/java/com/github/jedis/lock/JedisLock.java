package com.github.jedis.lock;

import java.util.Arrays;
import java.util.UUID;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisCluster;
import redis.clients.jedis.JedisCommands;

/**
 * 
 * @author Alois Belaska
 * @author kaidul
 */
public class JedisLock<T extends JedisCommands> {
  
    /**
     * Lua script which allows for an atomic delete on the lock only
     * if it is owned by the lock. This prevents locks stealing from others.
     */
    private final static String DELETE_IF_OWNED_LUA_SNIPPET =
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
            "return redis.call('del', KEYS[1]) " +
            "else " +
            "return 0 " +
            "end";
    
    private static final int ONE_SECOND = 1000;

    public static final int DEFAULT_EXPIRY_TIME_MILLIS = Integer.getInteger("com.github.jedis.lock.expiry.millis", 60 * ONE_SECOND);
    public static final int DEFAULT_ACQUIRE_TIMEOUT_MILLIS = Integer.getInteger("com.github.jedis.lock.acquiry.millis", 10 * ONE_SECOND);
    public static final int DEFAULT_ACQUIRY_RESOLUTION_MILLIS = Integer.getInteger("com.github.jedis.lock.acquiry.resolution.millis", 100);

    private final T jedis;

    private final String lockKey;

    private final int lockExpiryInMillis;
    private final int acquiryTimeoutInMillis;
    private final UUID lockUUID;
    
    private boolean isLocked;
    
    
    /**
     * Detailed constructor with default acquire timeout 10000 msecs and lock
     * expiration of 60000 msecs.
     * 
     * @param jedis
     *            Jedis or JedisCluster instance
     * @param lockKey
     *            lock key (ex. account:1, ...)
     */
    public JedisLock(T jedis, String lockKey) {
        this(jedis, lockKey, DEFAULT_ACQUIRE_TIMEOUT_MILLIS, DEFAULT_EXPIRY_TIME_MILLIS);
    }

    /**
     * Detailed constructor with default lock expiration of 60000 msecs.
     * 
     * @param jedis
     *            Jedis or JedisCluster instance
     * @param lockKey
     *            lock key (ex. account:1, ...)
     * @param acquireTimeoutMillis
     *            acquire timeout in miliseconds (default: 10000 msecs)
     */
    public JedisLock(T jedis, String lockKey, int acquireTimeoutMillis) {
        this(jedis, lockKey, acquireTimeoutMillis, DEFAULT_EXPIRY_TIME_MILLIS);
    }

    /**
     * Detailed constructor.
     * 
     * @param jedis
     *            Jedis or JedisCluster instance
     * @param lockKey
     *            lock key (ex. account:1, ...)
     * @param acquireTimeoutMillis
     *            acquire timeout in miliseconds (default: 10000 msecs)
     * @param expiryTimeMillis
     *            lock expiration in miliseconds (default: 60000 msecs)
     */
    public JedisLock(T jedis, String lockKey, int acquireTimeoutMillis, int expiryTimeMillis) {
        this(jedis, lockKey, acquireTimeoutMillis, expiryTimeMillis, UUID.randomUUID());
    }

    /**
     * Detailed constructor.
     * 
     * @param jedis
     *            Jedis or JedisCluster instance
     * @param lockKey
     *            lock key (ex. account:1, ...)
     * @param acquireTimeoutMillis
     *            acquire timeout in miliseconds (default: 10000 msecs)
     * @param expiryTimeMillis
     *            lock expiration in miliseconds (default: 60000 msecs)
     * @param uuid
     *            unique identification of this lock
     */
    public JedisLock(T jedis, String lockKey, int acquireTimeoutMillis, int expiryTimeMillis, UUID uuid) {
        this.jedis = jedis;
        this.lockKey = lockKey;
        this.acquiryTimeoutInMillis = acquireTimeoutMillis;
        this.lockExpiryInMillis = expiryTimeMillis+1;
        this.lockUUID = uuid;
        this.isLocked = false;
    }
    
    /**
     * @return lock uuid
     */
    public UUID getLockUUID() {
        return lockUUID;
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
     *            Jedis or JedisCluster instance
     * @return true if lock is acquired, false acquire timeout
     * @throws InterruptedException
     *             in case of thread interruption
     */
    protected synchronized boolean acquire(T jedis) throws InterruptedException {
        if(isLocked()) {
            return renew();
        }
        int timeout = acquiryTimeoutInMillis;
        while (timeout >= 0) {
            if ("OK".equals(jedis.set(lockKey, lockUUID.toString(), "NX", "PX", lockExpiryInMillis))) {
                return this.isLocked = true;
            }
            timeout -= DEFAULT_ACQUIRY_RESOLUTION_MILLIS;
            Thread.sleep(DEFAULT_ACQUIRY_RESOLUTION_MILLIS);
        }

        return false;
    }
    
    /**
     * Renew lock.
     * 
     * @return false if lock is not currently owned
     *         false if lock is currently owned by remote owner
     *         true otherwise
     * @throws InterruptedException
     *             in case of thread interruption
     */
    public synchronized boolean renew() throws InterruptedException {
        if(!isLocked() || isRemoteLocked()) {
          return false;
        }
        int timeout = acquiryTimeoutInMillis;
        while (timeout >= 0) {
            if ("OK".equals(jedis.set(lockKey, lockUUID.toString(), "XX", "PX", lockExpiryInMillis))) {
                return true;
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
     * @param jedis
     *            Jedis or JedisCluster instance
     */
    protected synchronized void release(T jedis) {
        if (isLocked()) {
            if(jedis instanceof Jedis) {
                ((Jedis) jedis).eval(DELETE_IF_OWNED_LUA_SNIPPET, Arrays.asList(lockKey), Arrays.asList(lockUUID.toString()));
            }
            else if(jedis instanceof JedisCluster) {
                ((JedisCluster) jedis).eval(DELETE_IF_OWNED_LUA_SNIPPET, Arrays.asList(lockKey), Arrays.asList(lockUUID.toString()));
            }
            this.isLocked = false;
        }
    }

    /**
     * Check if owns the lock
     * @return  true if lock owned
     */
    public synchronized boolean isLocked() {
        return this.isLocked;
    }
    
    /**
     * Check if the lock is owned by remote owner
     * @return  true if lock owned
     */
    public synchronized boolean isRemoteLocked() {
        if(this.isLocked()) {
            return false;
        }
        if(jedis.get(lockKey) == null) {
            return false;
        }
        return true;
    }


}
