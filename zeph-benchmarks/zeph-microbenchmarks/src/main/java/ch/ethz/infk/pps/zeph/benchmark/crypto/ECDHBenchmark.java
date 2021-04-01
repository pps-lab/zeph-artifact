package ch.ethz.infk.pps.zeph.benchmark.crypto;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Security;

import javax.crypto.KeyAgreement;
import javax.crypto.SecretKey;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

import ch.ethz.infk.pps.zeph.shared.CryptoUtil;

public class ECDHBenchmark {

	@Benchmark
	@Fork(value = 1, warmups = 1)
	@BenchmarkMode(Mode.All)
	public void testECDH(BenchmarkExecutionPlan benchPlan, Blackhole blackhole) {
		try {
			benchPlan.ecdh.doPhase(benchPlan.publicKey, true);
			SecretKey sharedKey = benchPlan.ecdh.generateSecret("AES");
			blackhole.consume(sharedKey);
		} catch (InvalidKeyException | IllegalStateException | NoSuchAlgorithmException e) {
			throw new IllegalStateException("failed", e);
		}

	}

	@State(Scope.Benchmark)
	public static class BenchmarkExecutionPlan {

		public PrivateKey privateKey;
		public PublicKey publicKey;
		public KeyAgreement ecdh;

		@Setup(Level.Trial)
		public void setupBenchmark() throws NoSuchAlgorithmException, NoSuchProviderException, InvalidKeyException {
			Security.addProvider(new BouncyCastleProvider());

			this.privateKey = CryptoUtil.generateKeyPair().getPrivate();
			this.publicKey = CryptoUtil.generateKeyPair().getPublic();

			this.ecdh = KeyAgreement.getInstance("ECDH", "BC");
			this.ecdh.init(privateKey);
		}

	}

}
