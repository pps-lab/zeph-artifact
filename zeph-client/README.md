# Zeph Data Producer

```Java
// create a facade that handles the communication with Kafka
ILocalTransformationFacade facade = new LocalTransformationFacade(kafkaBootstrapServers);

// create and initialize the data producer library with a configuration
LocalTransformation transformation = new LocalTransformation(config, universe, facade);
transformation.init();

// encode and encrypt the value + write to Kafka
transformation.submit(value, timestamp);
```

# Zeph Privacy Controller

```Java
// create a facade that handles the communication with Kafka
IPrivacyControllerFacade facade = new PrivacyControllerFacade(controllerId, kafkaBootstrapServers, timeout);

// create the privacy controller for the given configurations
PrivacyController privacyController = new PrivacyController(configs, universes, facade);

// start the privacy controller transformation phase
privacyController.run();


// stop the privacy controller
privacyController.requestShutdown();
```