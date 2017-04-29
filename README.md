# Jedis-lock

Jedis-lock is easy to use and simple implementation of distributed lock using Redis database and Jedis driver.

## How do I use it?

You can download the latests build at:
    http://github.com/abelaska/jedis-lock/downloads

Or use it as a maven dependency:

    <dependency>
        <groupId>com.github.jedis-lock</groupId>
        <artifactId>jedis-lock</artifactId>
        <version>1.0.0</version>
        <type>jar</type>
        <scope>compile</scope>
    </dependency>

To use it just:

    Jedis jedis = new Jedis("localhost");
    JedisLock<Jedis> lock = new JedisLock(jedis, "lockname", 10000, 30000);
    lock.acquire();
    try {
      // do some stuff
    }
    finally {
      lock.release();
    }

And to use it with `JedisCluster` everything is same except the `JedisLock` declaration:
    
    Jedis jedis = new Jedis("localhost");
    JedisLock<JedisCluster> lock = new JedisLock(jedis, "lockname", 10000, 30000);

That's it.

## License

The Apache Software License, Version 2.0
