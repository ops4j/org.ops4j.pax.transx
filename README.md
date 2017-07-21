Pax TransX
==========

Pax TransX aims at providing support for JTA/JTS transaction management in OSGi, along with resource pooling for JDBC and JMS.

This is the official source repository of the OPS4J Pax TransX project.
Its licensed under the Apache Software License 2.0 by the OPS4J community.

## Build

You'll need a machine with Java 8 and Apache Maven 3 installed.

Checkout:

    git clone git://github.com/ops4j/org.ops4j.pax.transx.git

Run Build:

    mvn clean install


## Releases

The latest release is 0.1.0 and can be found in [Maven Central](https://repo1.maven.org/maven2/org/ops4j/pax/transx).

## Transaction Manager API

The TransX Transaction Manager API aims to provide a common facade for transaction 
managers so that one can code against various transaction managers without caring about
the specifics.
In addition to the basic transaction management capabilities, the API provides a facade for:
 * Recovery
 * Last Resource Commit
 
The [Transaction Manager](https://github.com/ops4j/org.ops4j.pax.transx/blob/master/pax-transx-tm-api/src/main/java/org/ops4j/pax/transx/tm/TransactionManager.java) 
is the main entry point of the API.

## Recovery

Transactional resources must be wrapped into [ResourceFactories](https://github.com/ops4j/org.ops4j.pax.transx/blob/master/pax-transx-tm-api/src/main/java/org/ops4j/pax/transx/tm/ResourceFactory.java) 
and registered in the Transaction Manager.  This will trigger the recovery mechanism.

## Last Resource Commit

If a transactional resource does not support XA, the XAResource wrapping this resource should also implement [LastResource].
Transaction managers that support LRC will ensure that such resources are prepared last, ensuring some consistency even with no native XA support.
 
 ## Transaction Manager implementations
 
 Pax TransX supports 3 different implementations based on the following transaction managers:
  * Geronimo
  * Narayana
  * Atomikos
All three implementations supports recovery of inflight transactions, 
and Geronimo and Narayana transaction managers support Last-Resource-Commit.

## JDBC

A JDBC DataSource with pooling and XA support can be created in the following way:
```
return ManagedConnectionFactoryFactory.builder()
        .transactionManager(tm)
        .name("h2invm")
        .dataSource(xaDataSource)
        .build();
```

## JMS

A JMS ConnectionFactory with pooling an XA support can be created in the following way:
```
return ManagedConnectionFactoryFactory.builder()
        .transactionManager(tm)
        .name("vmbroker")
        .connectionFactory(new ActiveMQConnectionFactory(brokerUrl),
                           new ActiveMQXAConnectionFactory(brokerUrl))
        .build();
```

Note that the wrapped ConnectionFactory will support JMS 2.0, only relying 
on JMS 1.1 for the underlying connection factories.
