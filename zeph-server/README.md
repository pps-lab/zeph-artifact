# Zeph Data Transformer

The data transformer consists of a master application that is shared among all running transformations and a worker application that is specific for a privacy transformation. Both the master and the worker application can be started multiple times and Kafka Streams ensures to distribute the workload among the available instances.

## Data Transformer Master

```
Usage: zeph-server-master
    --bootstrap-server <HOST:PORT>        kafka bootstrap server host:port
    --delete-topics                       delete the created topics
    --state-dir <PATH>                    kafka streams state directory
    --stream-threads <NUMBER>             number of threads in kafka streams
    --time-to-commit <MILLIS>             number of milliseconds for
                                           privacy controllers to commit
    --universe-partitions <NUMBER>        number of topic partitions               
                                           for universe topics 
                                           (result and update topic)
    --universe-replications <NUMBER>      number of topic replications
                                           for universe topics
                                           (result and update topic)

```


## Data Transformer Worker

```
Usage: zeph-server-worker
    --bootstrap-server <HOST:PORT>        kafka bootstrap server host:port
    --delete-topics                       delete the created topics when
                                            shutting down
    --grace-size <MILLIS>                 grace size in milliseconds 
                                            (i.e. time late records are accepted)
    --partitions <NUMBER>                 number of partitions of token and
                                           value topics
    --replications <NUMBER>               number of replications of token and
                                           value topic
    --retention-time <HOURS>              retention period for streams state
    --state-dir <PATH>                    kafka streams state directory
    --stream-threads <NUMBER>             number of threads in kafka streams
    --universe-id <NUMBER>                universe id
    --universe-size <NUMBER>              universe size
    --window-size <MILLIS>                window size in milliseconds
```


# Plaintext Data Transformer

To run the Kafka Streams application that performs the transformation on plaintext:

```
Usage: zeph-server-worker
    plaintext
    --bootstrap-server <HOST:PORT>        kafka bootstrap server host:port
    --delete-topics                       delete the created topics when
                                            shutting down
    --grace-size <MILLIS>                 grace size in milliseconds 
                                            (i.e. time late records are accepted)
    --partitions <NUMBER>                 number of partitions of token and
                                           value topics
    --replications <NUMBER>               number of replications of token and
                                           value topic
    --retention-time <HOURS>              retention period for streams state
    --state-dir <PATH>                    kafka streams state directory
    --stream-threads <NUMBER>             number of threads in kafka streams
    --universe-id <NUMBER>                universe id
    --universe-size <NUMBER>              universe size
    --window-size <MILLIS>                window size in milliseconds
```