package com.github.jedis.lock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatcher;

import redis.clients.jedis.Jedis;

import com.github.jedis.lock.JedisLock.Lock;

public class JedisLockTest {

    private static final String LOCK_KEY = "foo";
    private static final int ACQUIRE_TIMEOUT = JedisLock.DEFAULT_ACQUIRY_RESOLUTION_MILLIS*3;
    private static final int LEASE_TIME = 5000;

    private Jedis jedis;
    private JedisLock lock;

    @Before
    public void setup() {
        jedis = mock(Jedis.class);
        lock = new JedisLock(jedis, LOCK_KEY, ACQUIRE_TIMEOUT, LEASE_TIME);
    }
    
    @Test
    public void shouldInvokeSetnxOnAcquire() throws Exception {
        TimeMatcher expectedTime = new TimeMatcher(System.currentTimeMillis() + LEASE_TIME);

        lock.acquire();
        
        verify(jedis, atLeastOnce()).setnx(eq(LOCK_KEY), argThat(expectedTime));
    }

    @Test
    public void shouldLockOnSuccessfulAcquire() throws Exception {
        whenSettingTokenOnRedisBeSuccessful();
        
        boolean result = lock.acquire();
        
        assertTrue(result);
        assertLockedDirectly();
    }

    @Test
    public void shouldRenewLockIfPresentLockByMeFoundAndNotExpired() throws Exception {
        setupCurrentTokenOnRedis(validTokenByMe());
        Thread.sleep(100l);
        
        boolean result = lock.acquire();
        
        assertTrue(result);
        assertLockedIndirectly();
    }

    @Test
    public void shouldNotLockIfPresentLockBySomeoneFoundAndNotExpired() throws Exception {
        setupCurrentTokenOnRedis(validTokenBySomeone());
        
        boolean result = lock.acquire();
        
        assertFalse(result);
        assertFalse(lock.isLocked());
    }

    @Test
    public void shouldAttemptLockIfLockFoundButExpired() throws Exception {
        setupCurrentTokenOnRedis(expiredTokenByMe());
        TimeMatcher expectedTimeElapsed = new TimeMatcher(System.currentTimeMillis() + LEASE_TIME);
        
        lock.acquire();
        
        verify(jedis, atLeastOnce()).getSet(eq(LOCK_KEY), argThat(expectedTimeElapsed));
    }

    @Test
    public void shouldSuccessfullyAcquiredExpiredLock() throws Exception {
        setupCurrentTokenOnRedis(expiredTokenByMe());

        boolean result = lock.acquire();
        
        assertTrue(result);
        assertLockedIndirectly();
    }

    @Test
    public void shouldInvokeDelOnReleaseOfOwnedLock() throws Exception {
        whenSettingTokenOnRedisBeSuccessful();
        
        lock.acquire();
        lock.release();
        
        verify(jedis).del(LOCK_KEY);
    }

    @Test
    public void shouldDesistAfterAcquireTimeoutElapsed() throws Exception {
        long beginTime = System.currentTimeMillis();
        setupCurrentTokenOnRedis(expiredTokenByMe());
        whenSettingTokenOnRedisFail();

        boolean result = lock.acquire();
        
        assertFalse(result);
        assertFalse(lock.isLocked());
        assertElapsed(beginTime, ACQUIRE_TIMEOUT);
    }

    @Test
    public void shouldRenewAcquireAnExpiredLockBySomeone() throws Exception {
        whenSettingTokenOnRedisBeSuccessful();
        setupCurrentTokenOnRedis(expiredTokenBySomeone());
        
        boolean result = lock.renew();
        
        assertTrue(result);
        assertLockedIndirectly();
    }

    @Test
    public void shouldRenewFailToRenewSomebodyElseLock() throws Exception {
        setupCurrentTokenOnRedis(validTokenBySomeone());
        
        boolean result = lock.renew();
        
        assertFalse(result);
        assertFalse(lock.isLocked());
    }

    @Test
    public void shouldRenewSuccedOverHisOwnLock() throws Exception {
        setupCurrentTokenOnRedis(validTokenByMe());
        
        boolean result = lock.renew();
        
        assertTrue(result);
        assertLockedIndirectly();
    }

    private void setupCurrentTokenOnRedis(String token) {
        whenSettingTokenOnRedisFail();
        when(jedis.get(anyString())).thenReturn(token );
        when(jedis.getSet(eq(LOCK_KEY), anyString())).thenReturn(token);
    }

    private void whenSettingTokenOnRedisBeSuccessful() {
        when(jedis.setnx(eq(LOCK_KEY), anyString())).thenReturn(1L);
    }

    private void whenSettingTokenOnRedisFail() {
        when(jedis.setnx(eq(LOCK_KEY), anyString())).thenReturn(0L);
        when(jedis.getSet(eq(LOCK_KEY), anyString())).thenReturn("boom!");
    }

    private String expiredTokenByMe() {
        return asLockString(0L);
    }

    private String validTokenByMe() {
        return asLockString(Long.MAX_VALUE);
    }

    private String expiredTokenBySomeone() {
        return asLockString(0l, UUID.randomUUID());
    }

    private String validTokenBySomeone() {
        return asLockString(Long.MAX_VALUE, UUID.randomUUID());
    }

    private String asLockString(long time) {
        return asLockString(time, lock.getLockUUID());
    }

    private String asLockString(long time, final UUID uuid) {
        return new JedisLock.Lock(uuid, time).toString();
    }

    private void assertLockedIndirectly() {
        assertLocked();
        verify(jedis, atLeastOnce()).getSet(eq(LOCK_KEY), anyString());
    }

    private void assertLockedDirectly() {
        assertLocked();
        verify(jedis, atLeastOnce()).setnx(eq(LOCK_KEY), anyString());
    }

    private void assertLocked() {
        assertTrue(lock.isLocked());
        assertEquals((double)lock.getLockExpiryTimeInMillis(), (double)System.currentTimeMillis() + LEASE_TIME, 55.0);
    }

    private void assertElapsed(long beginTime, int elapsedTime) {
        long elapsed = Math.abs(System.currentTimeMillis() - beginTime - elapsedTime);
        assertTrue(elapsed > 0);
        assertTrue(elapsed < JedisLock.DEFAULT_ACQUIRY_RESOLUTION_MILLIS+55);
    }

    public static class TimeMatcher extends ArgumentMatcher<String> {

        private long expectedTimeInMillis;
        
        public TimeMatcher(long expectedTimeMillis) {
            this.expectedTimeInMillis = expectedTimeMillis;
        }
        
        public boolean matches(Object target) {
            long expiryTimeInMillis = Lock.fromString((String)target).getExpiryTime();
            return Math.abs(expectedTimeInMillis - expiryTimeInMillis) < 55;
        }
    };
}
