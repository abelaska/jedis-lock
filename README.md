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
    JedisLock lock = new JedisLock(jedis, "lockname", 10000, 30000);
    lock.acquire();
    try {
      // do some stuff
    }
    finally {
      lock.release();
    }

To use it with cluster:

    JedisCluster jedisCluster = new JedisCluster( /* Set<HostAndPort> hostAndrPortSet */ );
    JedisLock lock = new JedisLock(jedisCluster, "lockname", 10000, 30000);

That's it.

## License

The Apache Software License, Version 2.0
