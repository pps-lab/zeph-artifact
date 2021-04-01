# Zeph Crypto

## Stream Encryption

```Java
SecretKey secretKey = CryptoUtil.generateKey();
IHeac heac = new Heac(secretKey);
Digest msg = ... ;
Digest ct = encHeac.encrypt(timestamp, msg, prevTimestamp);
Digest result = decHeac.decrypt(timestamp, ct, prevTimestamp);
```

## Secure Aggregation

### Zeph Optimization
```Java
// initialize secure aggregation native
SecureAggregationNative secureAggregation = new SecureAggregationNative();
secureAggregation.addSharedKeys(sharedKeys);

// clear graphs of previous epoch
secureAggregation.clearEpochNeighbourhoodsER(epoch -1, nodeIds);

// build graphs of epoch
secureAggregation.buildEpochNeighbourhoodsER(epoch, id, otherIds, k);

// create dummy key sum for specific window
Digest dummyKeySum = secureAggregation.getDummyKeySumER(window, epoch, roundIdx, id, dropped);

```

### Strawman

```Java
// initialize secure aggregation native
SecureAggregationNative secureAggregation = new SecureAggregationNative();
secureAggregation.addSharedKeys(sharedKeys);

// create dummy key sum for specific window using the Strawman
Digest dummyKeySum = secureAggregationStrawman.getDummyKeySum(window, id, otherIds);
```

### Dream
```Java
// initialize secure aggregation Dream native
SecureAggregationDreamNative secureAggregationDream = new SecureAggregationDreamNative();
secureAggregationDream.addSharedKeys(sharedKeys);

// create dummy key sum for specific window according to Dream
Digest dummyKeySum = secureAggregationDream.getDummyKeySum(r1, r2, id, otherIds, k);

```