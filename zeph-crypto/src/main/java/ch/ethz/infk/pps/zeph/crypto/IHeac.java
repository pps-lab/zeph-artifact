package ch.ethz.infk.pps.zeph.crypto;

import ch.ethz.infk.pps.shared.avro.Digest;

public interface IHeac {

	public Digest encrypt(long msgTimestamp, Digest msg, long prevTimestamp);

	public Digest decrypt(long ctTimestamp, Digest ct, long prevTimestamp);

	public Digest getKey(long maxPrevTime, long maxTime);

}
