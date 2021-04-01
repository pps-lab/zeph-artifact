# Microbenchmarks

The microbenchmark use JMH to run the experiments. The following parameters are recommended:

```
Usage:
    testLocalTransformation testECDH testErdosRenyiNative testStrawmanNative 
    testDreamNative testStrawmanNativeLatency testDreamNativeLatency 
    testErdosRenyiNativeLatency testErdosRenyiBuildNeighbourhoodNativeLatency 
    testErdosRenyiClearNeighbourhoodNativeLatency testUniverseDelta 
    testNonMPCTransformationToken testEncode testEncodeEncrypt testEncryptVectorElement

    -rf json                                define to write json file
    -rff <PATH>                             path of the result json file
    -p dataDir=<PATH>                       location of the identities and
                                              shared keys
    -p size_win=<LIST>                      list of universe and window 
                                              size tuples (_ separator)
    -p bench_time_ms=<MILLIS>               config bench time
    -p window_size_ms=<MILLIS>              config window size
    -p universe_size=<LIST>                 config universe sizes
    -p addE_addNE_dropE_dropNE=<LIST>       config dropped and returned
    -p encoderConfig=<LIST>                 config for encoding

```

# End-to-End Benchmark

## Zeph Load Generator

```
Usage: benchmark
    --end-to-end                            run end to end benchmark
    --zeph
    --e2e-controller                        run the end-to-end controller
    --e2e-producer                          run the end-to-end producer
    --experiment-id <ID>                    unique id of experiment
                                             (cannot be repeated) must be the
                                             same on all partiton instances
    --test-time <SECS>                      number of seconds to run the test
    --application <FOLDER>                  folder name within data folder
                                             with producer stream csv files
    --data-dir <PATH>                       base folder for producer info
                                             and shared keys
    --delete-topics                         delete the partition sync topic
                                             at the end   
    --expo-delay-mean <NUMBER>              exponential distribution lambda 
                                             (sample time between two
                                             requests from same producer)
    --kafka-bootstrap-server <HOST:PORT>    kafka bootstrap server host:port
    --out-file <FILE>                       name of output file

    --universe-size <NUMBER>                number of participants in a
                                            universe
    --universes <LIST>                      (uId1, ..., uIdN)
    --window-size <MILLIS>                  size of the window in milliseconds
    --producer-partition <NUMBER>           producer partition, must be in
                                             range [0, producer-partition-count)
    --producer-partition-count <NUMBER>     number of partitions per
                                            universe
    --producer-partition-size <NUMBER>      number of producers per
                                            universe (i.e. producer
                                            partition size)
    --controller-partition <NUMBER>         controller partition, must be
                                              in range [0,controller-partition-count)
    --controller-partition-count <NUMBER>   number of controller partitions
    --controller-partition-size <NUMBER>    number of controllers per
                                              universe (i.e. controller partition size)
    --controller-poll-timeout <MILLIS>      poll timeout in millis
    --alpha <PROB>                          probability that a member is
                                             non-colluding
    --delta <PROB>                          failure error bound of erdos
                                             renyi secure aggregation
    --producers-in-controller <NUMBER>      number of producers assigned 
                                             to privacy controller per universe
    --universe-min-size <NUMBER>            minimum number of participants
                                             required in a universe
    --universes-in-controller <NUMBER>      number of universes assigned
                                             to a privacy controller
```


## Plaintext Load Generator

```
usage: benchmark
    --end-to-end                            run end to end benchmark
    --plaintext                             run the plaintext version
    --e2e-producer                          run the end-to-end producer
    --experiment-id <ID>                    unique id of experiment
                                             (cannot be repeated) must be the
                                             same on all partiton instances
    --test-time <SECS>                      number of seconds to run the test
    --application <FOLDER>                  folder name within data folder
                                             with producer stream csv files
    --data-dir <PATH>                       base folder for producer info
                                             and shared keys
    --delete-topics                         delete the partition sync topic
                                             at the end   
    --expo-delay-mean <NUMBER>              exponential distribution lambda 
                                             (sample time between two
                                             requests from same producer)
    --kafka-bootstrap-server <HOST:PORT>    kafka bootstrap server host:port
    --out-file <FILE>                       name of output file
    --producer-partition <NUMBER>           producer partition, must be in
                                             range [0, producer-partition-count)
    --producer-partition-count <NUMBER>     number of partitions per
                                            universe
    --producer-partition-size <NUMBER>      number of producers per
                                            universe (i.e. producer
                                            partition size)
    --universe-size <NUMBER>                number of participants in a 
                                              universe
    --universes <LIST>                      (uId1, ..., uIdN)
    --window-size <MILLIS>                  size of the window in milliseconds
```