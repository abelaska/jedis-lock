# Jedis-lock

The [master](https://github.com/abelaska/jedis-lock) branch is no longer maintained/pull requests are not merged by the project owner. This fork has following additional features: 
+ [Improvments of pending pull requests](https://github.com/abelaska/jedis-lock/pulls)
..+ New `SET` API in place of `SETNX`
..+ Lock ownership safety on `release()`
..+ Locking support for `JedisCluster`


Jedis-lock is easy to use and simple implementation of distributed lock using Redis database and Jedis driver.

## How do I use it?

You can download the latests build at:
    http://github.com/kaidul/jedis-lock/downloads

Or use it as a maven dependency:

    <dependency>
        <groupId>com.github.jedis-lock</groupId>
        <artifactId>jedis-lock</artifactId>
        <version>2.0.0</version>
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
