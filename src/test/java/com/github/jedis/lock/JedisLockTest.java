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

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatcher;

import redis.clients.jedis.Jedis;

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
        when(jedis.setnx(eq(LOCK_KEY), anyString())).thenReturn(1L);
        
        boolean result = lock.acquire();
        
        assertTrue(result);
        assertLocked();
    }

    @Test
    public void shouldNotLockIfPresentLockFoundAndNotExpired() throws Exception {
        when(jedis.setnx(anyString(), anyString())).thenReturn(0L);
        when(jedis.get(anyString())).thenReturn(String.valueOf(Long.MAX_VALUE));
        
        boolean result = lock.acquire();
        
        assertFalse(result);
        assertFalse(lock.isLocked());
    }

    @Test
    public void shouldAttemptLockIfLockFoundButExpired() throws Exception {
        TimeMatcher expectedTime = new TimeMatcher(System.currentTimeMillis() + LEASE_TIME);
        when(jedis.setnx(eq(LOCK_KEY), anyString())).thenReturn(0L);
        when(jedis.get(anyString())).thenReturn(String.valueOf(0L));
        
        lock.acquire();
        
        verify(jedis, atLeastOnce()).getSet(eq(LOCK_KEY), argThat(expectedTime));
    }

    @Test
    public void shouldLockIfFoundButExpiredWhenSuccessfulAcquire() throws Exception {
        final String currentLock = String.valueOf(999L);
        when(jedis.setnx(eq(LOCK_KEY), anyString())).thenReturn(0L);
        when(jedis.get(anyString())).thenReturn(currentLock);
        when(jedis.getSet(eq(LOCK_KEY), anyString())).thenReturn(currentLock);
        
        boolean result = lock.acquire();
        
        assertTrue(result);
        assertLocked();
    }

    @Test
    public void shouldInvokeDelOnReleaseOfOwnedLock() throws Exception {
        when(jedis.setnx(eq(LOCK_KEY), anyString())).thenReturn(1L);
        
        lock.acquire();
        lock.release();
        
        verify(jedis).del(LOCK_KEY);
    }

    @Test
    public void shouldDesistAfterAcquireTimeoutElapsed() throws Exception {
        long beginTime = System.currentTimeMillis();
        when(jedis.setnx(anyString(), anyString())).thenReturn(0L);
        when(jedis.get(anyString())).thenReturn(null);
        
        boolean result = lock.acquire();
        
        assertFalse(result);
        assertFalse(lock.isLocked());
        assertElapsed(beginTime, ACQUIRE_TIMEOUT);
    }

    private void assertLocked() {
        assertTrue(lock.isLocked());
        assertEquals((double)lock.getExpiryTimeInMillis(), (double)System.currentTimeMillis() + LEASE_TIME, 55.0);
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
            long expiryTimeInMillis = Long.parseLong((String)target);
            return Math.abs(expectedTimeInMillis - expiryTimeInMillis) < 55;
        }
    };
}
