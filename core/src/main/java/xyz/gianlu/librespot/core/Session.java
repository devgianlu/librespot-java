package xyz.gianlu.librespot.core;

import com.google.protobuf.ByteString;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xyz.gianlu.librespot.Version;
import xyz.gianlu.librespot.cache.CacheManager;
import xyz.gianlu.librespot.cdn.CdnManager;
import xyz.gianlu.librespot.crypto.BlobUtils;
import xyz.gianlu.librespot.common.NameThreadFactory;
import xyz.gianlu.librespot.common.Utils;
import xyz.gianlu.librespot.common.config.AuthConf;
import xyz.gianlu.librespot.common.config.Configuration;
import xyz.gianlu.librespot.common.enums.DeviceType;
import xyz.gianlu.librespot.common.proto.Authentication;
import xyz.gianlu.librespot.common.proto.Keyexchange;
import xyz.gianlu.librespot.crypto.CipherPair;
import xyz.gianlu.librespot.crypto.DiffieHellman;
import xyz.gianlu.librespot.crypto.Packet;
import xyz.gianlu.librespot.mercury.MercuryClient;
import xyz.gianlu.librespot.player.AudioKeyManager;
import xyz.gianlu.librespot.player.Player;
import xyz.gianlu.librespot.player.feeders.storage.ChannelManager;
import xyz.gianlu.librespot.spirc.SpotifyIrc;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.RSAPublicKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Gianlu
 */
public class Session implements Closeable {
    private static final Logger LOGGER = Logger.getLogger(Session.class);
    private static final String PREFERRED_LOCALE = "en";
    private static final byte[] serverKey = new byte[]{
            (byte) 0xac, (byte) 0xe0, (byte) 0x46, (byte) 0x0b, (byte) 0xff, (byte) 0xc2, (byte) 0x30, (byte) 0xaf, (byte) 0xf4, (byte) 0x6b, (byte) 0xfe, (byte) 0xc3,
            (byte) 0xbf, (byte) 0xbf, (byte) 0x86, (byte) 0x3d, (byte) 0xa1, (byte) 0x91, (byte) 0xc6, (byte) 0xcc, (byte) 0x33, (byte) 0x6c, (byte) 0x93, (byte) 0xa1,
            (byte) 0x4f, (byte) 0xb3, (byte) 0xb0, (byte) 0x16, (byte) 0x12, (byte) 0xac, (byte) 0xac, (byte) 0x6a, (byte) 0xf1, (byte) 0x80, (byte) 0xe7, (byte) 0xf6,
            (byte) 0x14, (byte) 0xd9, (byte) 0x42, (byte) 0x9d, (byte) 0xbe, (byte) 0x2e, (byte) 0x34, (byte) 0x66, (byte) 0x43, (byte) 0xe3, (byte) 0x62, (byte) 0xd2,
            (byte) 0x32, (byte) 0x7a, (byte) 0x1a, (byte) 0x0d, (byte) 0x92, (byte) 0x3b, (byte) 0xae, (byte) 0xdd, (byte) 0x14, (byte) 0x02, (byte) 0xb1, (byte) 0x81,
            (byte) 0x55, (byte) 0x05, (byte) 0x61, (byte) 0x04, (byte) 0xd5, (byte) 0x2c, (byte) 0x96, (byte) 0xa4, (byte) 0x4c, (byte) 0x1e, (byte) 0xcc, (byte) 0x02,
            (byte) 0x4a, (byte) 0xd4, (byte) 0xb2, (byte) 0x0c, (byte) 0x00, (byte) 0x1f, (byte) 0x17, (byte) 0xed, (byte) 0xc2, (byte) 0x2f, (byte) 0xc4, (byte) 0x35,
            (byte) 0x21, (byte) 0xc8, (byte) 0xf0, (byte) 0xcb, (byte) 0xae, (byte) 0xd2, (byte) 0xad, (byte) 0xd7, (byte) 0x2b, (byte) 0x0f, (byte) 0x9d, (byte) 0xb3,
            (byte) 0xc5, (byte) 0x32, (byte) 0x1a, (byte) 0x2a, (byte) 0xfe, (byte) 0x59, (byte) 0xf3, (byte) 0x5a, (byte) 0x0d, (byte) 0xac, (byte) 0x68, (byte) 0xf1,
            (byte) 0xfa, (byte) 0x62, (byte) 0x1e, (byte) 0xfb, (byte) 0x2c, (byte) 0x8d, (byte) 0x0c, (byte) 0xb7, (byte) 0x39, (byte) 0x2d, (byte) 0x92, (byte) 0x47,
            (byte) 0xe3, (byte) 0xd7, (byte) 0x35, (byte) 0x1a, (byte) 0x6d, (byte) 0xbd, (byte) 0x24, (byte) 0xc2, (byte) 0xae, (byte) 0x25, (byte) 0x5b, (byte) 0x88,
            (byte) 0xff, (byte) 0xab, (byte) 0x73, (byte) 0x29, (byte) 0x8a, (byte) 0x0b, (byte) 0xcc, (byte) 0xcd, (byte) 0x0c, (byte) 0x58, (byte) 0x67, (byte) 0x31,
            (byte) 0x89, (byte) 0xe8, (byte) 0xbd, (byte) 0x34, (byte) 0x80, (byte) 0x78, (byte) 0x4a, (byte) 0x5f, (byte) 0xc9, (byte) 0x6b, (byte) 0x89, (byte) 0x9d,
            (byte) 0x95, (byte) 0x6b, (byte) 0xfc, (byte) 0x86, (byte) 0xd7, (byte) 0x4f, (byte) 0x33, (byte) 0xa6, (byte) 0x78, (byte) 0x17, (byte) 0x96, (byte) 0xc9,
            (byte) 0xc3, (byte) 0x2d, (byte) 0x0d, (byte) 0x32, (byte) 0xa5, (byte) 0xab, (byte) 0xcd, (byte) 0x05, (byte) 0x27, (byte) 0xe2, (byte) 0xf7, (byte) 0x10,
            (byte) 0xa3, (byte) 0x96, (byte) 0x13, (byte) 0xc4, (byte) 0x2f, (byte) 0x99, (byte) 0xc0, (byte) 0x27, (byte) 0xbf, (byte) 0xed, (byte) 0x04, (byte) 0x9c,
            (byte) 0x3c, (byte) 0x27, (byte) 0x58, (byte) 0x04, (byte) 0xb6, (byte) 0xb2, (byte) 0x19, (byte) 0xf9, (byte) 0xc1, (byte) 0x2f, (byte) 0x02, (byte) 0xe9,
            (byte) 0x48, (byte) 0x63, (byte) 0xec, (byte) 0xa1, (byte) 0xb6, (byte) 0x42, (byte) 0xa0, (byte) 0x9d, (byte) 0x48, (byte) 0x25, (byte) 0xf8, (byte) 0xb3,
            (byte) 0x9d, (byte) 0xd0, (byte) 0xe8, (byte) 0x6a, (byte) 0xf9, (byte) 0x48, (byte) 0x4d, (byte) 0xa1, (byte) 0xc2, (byte) 0xba, (byte) 0x86, (byte) 0x30,
            (byte) 0x42, (byte) 0xea, (byte) 0x9d, (byte) 0xb3, (byte) 0x08, (byte) 0x6c, (byte) 0x19, (byte) 0x0e, (byte) 0x48, (byte) 0xb3, (byte) 0x9d, (byte) 0x66,
            (byte) 0xeb, (byte) 0x00, (byte) 0x06, (byte) 0xa2, (byte) 0x5a, (byte) 0xee, (byte) 0xa1, (byte) 0x1b, (byte) 0x13, (byte) 0x87, (byte) 0x3c, (byte) 0xd7,
            (byte) 0x19, (byte) 0xe6, (byte) 0x55, (byte) 0xbd
    };
    private final DiffieHellman keys;
    private final ExecutorService executorService = Executors.newCachedThreadPool(new NameThreadFactory(r -> "handle-packet-" + r.hashCode()));
    private final AtomicBoolean authLock = new AtomicBoolean(false);
    private ConnectionHolder conn;
    private CipherPair cipherPair;
    private Receiver receiver;
    private Authentication.APWelcome apWelcome = null;
    private MercuryClient mercuryClient;
    private SpotifyIrc spirc;
    private Player player;
    private AudioKeyManager audioKeyManager;
    private ChannelManager channelManager;
    private TokenProvider tokenProvider;
    private CdnManager cdnManager;
    private CacheManager cacheManager;
    private String countryCode = null;
    private volatile boolean closed = false;
    private Configuration conf;
    private SecureRandom random;


    protected Session(Configuration conf) throws IOException {
        Socket socket = ApResolver.getSocketFromRandomAccessPoint();
        this.random = new SecureRandom();
        this.keys = new DiffieHellman(new SecureRandom());
        this.conn = new ConnectionHolder(socket);
        this.conf = conf;
        LOGGER.info(String.format("Created new session! {deviceId: %s, ap: %s} ", conf.getDeviceId(), socket.getInetAddress()));
    }

    void connect() throws IOException, GeneralSecurityException, SpotifyAuthenticationException {
        Accumulator acc = new Accumulator();

        // Send ClientHello

        byte[] nonce = new byte[0x10];
        random.nextBytes(nonce);

        Keyexchange.ClientHello clientHello = Keyexchange.ClientHello.newBuilder()
                .setBuildInfo(Keyexchange.BuildInfo.newBuilder()
                        .setProduct(Keyexchange.Product.PRODUCT_PARTNER)
                        .setPlatform(Keyexchange.Platform.PLATFORM_LINUX_X86)
                        .setVersion(110713766)
                        .build())
                .addCryptosuitesSupported(Keyexchange.Cryptosuite.CRYPTO_SUITE_SHANNON)
                .setLoginCryptoHello(Keyexchange.LoginCryptoHelloUnion.newBuilder()
                        .setDiffieHellman(Keyexchange.LoginCryptoDiffieHellmanHello.newBuilder()
                                .setGc(ByteString.copyFrom(keys.publicKeyArray()))
                                .setServerKeysKnown(1)
                                .build())
                        .build())
                .setClientNonce(ByteString.copyFrom(nonce))
                .setPadding(ByteString.copyFrom(new byte[]{0x1e}))
                .build();

        byte[] clientHelloBytes = clientHello.toByteArray();
        int length = 2 + 4 + clientHelloBytes.length;
        conn.out.writeByte(0);
        conn.out.writeByte(4);
        conn.out.writeInt(length);
        conn.out.write(clientHelloBytes);
        conn.out.flush();

        acc.writeByte(0);
        acc.writeByte(4);
        acc.writeInt(length);
        acc.write(clientHelloBytes);


        // Read APResponseMessage

        length = conn.in.readInt();
        acc.writeInt(length);
        byte[] buffer = new byte[length - 4];
        conn.in.readFully(buffer);
        acc.write(buffer);
        acc.dump();

        Keyexchange.APResponseMessage apResponseMessage = Keyexchange.APResponseMessage.parseFrom(buffer);
        byte[] sharedKey = Utils.toByteArray(keys.computeSharedKey(apResponseMessage.getChallenge().getLoginCryptoChallenge().getDiffieHellman().getGs().toByteArray()));


        // Check gs_signature

        KeyFactory factory = KeyFactory.getInstance("RSA");
        PublicKey publicKey = factory.generatePublic(new RSAPublicKeySpec(new BigInteger(1, serverKey), BigInteger.valueOf(65537)));

        Signature sig = Signature.getInstance("SHA1withRSA");
        sig.initVerify(publicKey);
        sig.update(apResponseMessage.getChallenge().getLoginCryptoChallenge().getDiffieHellman().getGs().toByteArray());
        if (!sig.verify(apResponseMessage.getChallenge().getLoginCryptoChallenge().getDiffieHellman().getGsSignature().toByteArray()))
            throw new GeneralSecurityException("Failed signature check!");


        // Solve challenge

        ByteArrayOutputStream data = new ByteArrayOutputStream(0x64);

        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(sharedKey, "HmacSHA1"));
        for (int i = 1; i < 6; i++) {
            mac.update(acc.array());
            mac.update(new byte[]{(byte) i});
            data.write(mac.doFinal());
            mac.reset();
        }

        byte[] dataArray = data.toByteArray();
        mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(Arrays.copyOfRange(dataArray, 0, 0x14), "HmacSHA1"));
        mac.update(acc.array());

        byte[] challenge = mac.doFinal();
        Keyexchange.ClientResponsePlaintext clientResponsePlaintext = Keyexchange.ClientResponsePlaintext.newBuilder()
                .setLoginCryptoResponse(Keyexchange.LoginCryptoResponseUnion.newBuilder()
                        .setDiffieHellman(Keyexchange.LoginCryptoDiffieHellmanResponse.newBuilder()
                                .setHmac(ByteString.copyFrom(challenge))
                                .build())
                        .build())
                .setPowResponse(Keyexchange.PoWResponseUnion.newBuilder()
                        .build())
                .setCryptoResponse(Keyexchange.CryptoResponseUnion.newBuilder()
                        .build())
                .build();

        byte[] clientResponsePlaintextBytes = clientResponsePlaintext.toByteArray();
        length = 4 + clientResponsePlaintextBytes.length;
        conn.out.writeInt(length);
        conn.out.write(clientResponsePlaintextBytes);
        conn.out.flush();

        try {
            byte[] scrap = new byte[4];
            conn.socket.setSoTimeout((int) TimeUnit.SECONDS.toMillis(1));
            int read = conn.in.read(scrap);
            if (read == scrap.length) {
                length = (scrap[0] << 24) | (scrap[1] << 16) | (scrap[2] << 8) | (scrap[3] & 0xFF);
                byte[] payload = new byte[length - 4];
                conn.in.readFully(payload);
                Keyexchange.APLoginFailed failed = Keyexchange.APResponseMessage.parseFrom(payload).getLoginFailed();
                throw new SpotifyAuthenticationException(failed);
            } else if (read > 0) {
                throw new IllegalStateException("Read unknown data!");
            }
        } catch (SocketTimeoutException ignored) {
        } finally {
            conn.socket.setSoTimeout(0);
        }


        // Init Shannon cipher

        cipherPair = new CipherPair(Arrays.copyOfRange(data.toByteArray(), 0x14, 0x34),
                Arrays.copyOfRange(data.toByteArray(), 0x34, 0x54));

        synchronized (authLock) {
            authLock.set(true);
        }

        LOGGER.info("Connected successfully!");
    }

    void authenticate(@NotNull Authentication.LoginCredentials credentials, String deviceId) throws IOException, GeneralSecurityException, SpotifyAuthenticationException, SpotifyIrc.IrcException {
        authenticatePartial(credentials,deviceId);

        mercuryClient = new MercuryClient(this);
        tokenProvider = new TokenProvider(this);
        audioKeyManager = new AudioKeyManager(this);
        channelManager = new ChannelManager(this);
        cdnManager = new CdnManager(this);
        if(this.conf.getCache().isEnabled()) cacheManager = new CacheManager(conf.getCache());
        spirc = new SpotifyIrc(this, this.conf.getPlayer());
        spirc.sayHello();
        player = new Player(this.conf.getPlayer(), this);

        LOGGER.info(String.format("Authenticated as %s!", apWelcome.getCanonicalUsername()));
    }

    private void authenticatePartial(@NotNull Authentication.LoginCredentials credentials, String deviceId) throws IOException, GeneralSecurityException, SpotifyAuthenticationException {
        if (cipherPair == null) throw new IllegalStateException("Connection not established!");

        Authentication.ClientResponseEncrypted clientResponseEncrypted = Authentication.ClientResponseEncrypted.newBuilder()
                .setLoginCredentials(credentials)
                .setSystemInfo(Authentication.SystemInfo.newBuilder()
                        .setOs(Authentication.Os.OS_UNKNOWN)
                        .setCpuFamily(Authentication.CpuFamily.CPU_UNKNOWN)
                        .setSystemInformationString(Version.systemInfoString())
                        .setDeviceId(deviceId)
                        .build())
                .setVersionString(Version.versionString())
                .build();

        sendUnchecked(Packet.Type.Login, clientResponseEncrypted.toByteArray());

        Packet packet = cipherPair.receiveEncoded(conn.in);
        if (packet.is(Packet.Type.APWelcome)) {
            apWelcome = Authentication.APWelcome.parseFrom(packet.payload);

            receiver = new Receiver();
            new Thread(receiver, "session-packet-receiver").start();

            byte[] bytes0x0f = new byte[20];
            random().nextBytes(bytes0x0f);
            sendUnchecked(Packet.Type.Unknown_0x0f, bytes0x0f);

            ByteBuffer preferredLocale = ByteBuffer.allocate(18 + 5);
            preferredLocale.put((byte) 0x0).put((byte) 0x0).put((byte) 0x10).put((byte) 0x0).put((byte) 0x02);
            preferredLocale.put("preferred-locale".getBytes());
            preferredLocale.put(PREFERRED_LOCALE.getBytes());
            sendUnchecked(Packet.Type.PreferredLocale, preferredLocale.array());

            synchronized (authLock) {
                authLock.set(false);
                authLock.notifyAll();
            }
        } else if (packet.is(Packet.Type.AuthFailure)) {
            throw new SpotifyAuthenticationException(Keyexchange.APLoginFailed.parseFrom(packet.payload));
        } else {
            throw new IllegalStateException("Unknown CMD 0x" + Integer.toHexString(packet.cmd));
        }
    }

    @Override
    public void close() throws IOException {
        if (receiver != null) {
            receiver.stop();
            receiver = null;
        }

        if (player != null) {
            player.close();
            player = null;
        }

        if (audioKeyManager != null) {
            audioKeyManager.close();
            audioKeyManager = null;
        }

        if (channelManager != null) {
            channelManager.close();
            channelManager = null;
        }

        if (spirc != null) {
            spirc.close();
            spirc = null;
        }

        if (mercuryClient != null) {
            mercuryClient.close();
            mercuryClient = null;
        }

        executorService.shutdown();
        conn.socket.close();

        apWelcome = null;
        cipherPair = null;
        closed = true;

        LOGGER.info(String.format("Closed session. {deviceId: %s, ap: %s} ", this.conf.getDeviceId(), conn.socket.getInetAddress()));
    }

    private void sendUnchecked(Packet.Type cmd, byte[] payload) throws IOException {
        cipherPair.sendEncoded(conn.out, cmd.val, payload);
    }

    private void waitAuthLock() {
        if (closed) throw new IllegalStateException("Session is closed!");

        synchronized (authLock) {
            if (cipherPair == null || authLock.get()) {
                try {
                    authLock.wait();
                } catch (InterruptedException ex) {
                    throw new RuntimeException(ex);
                }
            }
        }
    }

    public void send(Packet.Type cmd, byte[] payload) throws IOException {
        waitAuthLock();
        sendUnchecked(cmd, payload);
    }

    @NotNull
    public MercuryClient mercury() {
        waitAuthLock();
        if (mercuryClient == null) throw new IllegalStateException("Session isn't authenticated!");
        return mercuryClient;
    }

    @NotNull
    public AudioKeyManager audioKey() {
        waitAuthLock();
        if (audioKeyManager == null) throw new IllegalStateException("Session isn't authenticated!");
        return audioKeyManager;
    }

    @NotNull
    public CacheManager cache() {
        waitAuthLock();
        if (cacheManager == null) throw new IllegalStateException("Session isn't authenticated!");
        return cacheManager;
    }

    @NotNull
    public CdnManager cdn() {
        waitAuthLock();
        if (cdnManager == null) throw new IllegalStateException("Session isn't authenticated!");
        return cdnManager;
    }

    @NotNull
    public ChannelManager channel() {
        waitAuthLock();
        if (channelManager == null) throw new IllegalStateException("Session isn't authenticated!");
        return channelManager;
    }

    @NotNull
    public TokenProvider tokens() {
        waitAuthLock();
        if (tokenProvider == null) throw new IllegalStateException("Session isn't authenticated!");
        return tokenProvider;
    }

    @NotNull
    public SpotifyIrc spirc() {
        waitAuthLock();
        if (spirc == null) throw new IllegalStateException("Session isn't authenticated!");
        return spirc;
    }

    @NotNull
    public Player player() {
        waitAuthLock();
        if (player == null) throw new IllegalStateException("Session isn't authenticated!");
        return player;
    }

    @NotNull
    public Authentication.APWelcome apWelcome() {
        waitAuthLock();
        if (apWelcome == null) throw new IllegalStateException("Session isn't authenticated!");
        return apWelcome;
    }

    public boolean valid() {
        waitAuthLock();
        return apWelcome != null && conn != null && !conn.socket.isClosed() && !closed;
    }

    @NotNull
    public String deviceId() {
        return this.conf.getDeviceId();
    }

    @NotNull
    public DeviceType deviceType() {
        return this.conf.getDeviceType();
    }

    @NotNull
    ExecutorService executor() {
        return executorService;
    }

    @NotNull
    public String deviceName() {
        return this.conf.getDeviceName();
    }

    @NotNull
    public Random random() {
        return random;
    }

    private void reconnect() {
        try {
            if (conn != null) {
                conn.socket.close();
                receiver.stop();
            }

            conn = new ConnectionHolder(ApResolver.getSocketFromRandomAccessPoint());
            connect();
            authenticatePartial(Authentication.LoginCredentials.newBuilder()
                    .setUsername(apWelcome.getCanonicalUsername())
                    .setTyp(apWelcome.getReusableAuthCredentialsType())
                    .setAuthData(apWelcome.getReusableAuthCredentials())
                    .build(), this.conf.getDeviceId());

            spirc.sayHello();

            LOGGER.info(String.format("Re-authenticated as %s!", apWelcome.getCanonicalUsername()));
        } catch (IOException | GeneralSecurityException | SpotifyAuthenticationException | SpotifyIrc.IrcException ex) {
            throw new RuntimeException("Failed reconnecting!", ex);
        }


    }




    @Nullable
    public String countryCode() {
        return countryCode;
    }


     public static class Builder {
        private Authentication.LoginCredentials loginCredentials = null;
        private Session session;

        public Builder(Configuration conf) throws IOException {
            this.session = new Session(conf);
        }

        @NotNull
        public Builder facebook() throws IOException {
            try (FacebookAuthenticator authenticator = new FacebookAuthenticator()) {
                loginCredentials = authenticator.lockUntilCredentials();
                return this;
            } catch (InterruptedException ex) {
                throw new IOException(ex);
            }
        }

        @NotNull
        public Builder blob(String username, byte[] blob) throws GeneralSecurityException, IOException {
            loginCredentials = BlobUtils.decryptBlob(username, blob, session.conf.getDeviceId());
            return this;
        }

        @NotNull
        public Builder userPass(@NotNull String username, @NotNull String password) {
            loginCredentials = Authentication.LoginCredentials.newBuilder()
                    .setUsername(username)
                    .setTyp(Authentication.AuthenticationType.AUTHENTICATION_USER_PASS)
                    .setAuthData(ByteString.copyFromUtf8(password))
                    .build();
            return this;
        }

        @NotNull
        public Session create() throws IOException, GeneralSecurityException, SpotifyAuthenticationException, SpotifyIrc.IrcException {
            AuthConf authConf = session.conf.getAuth();
            if (loginCredentials == null) {
                if (authConf != null) {
                    String blob = authConf.getBlob();
                    String username = authConf.getUsername();
                    String password = authConf.getPassword();

                    switch (authConf.getStrategy()) {
                        case FACEBOOK:
                            facebook();
                            break;
                        case BLOB:
                            if (username == null) throw new IllegalArgumentException("Missing authUsername!");
                            if (blob == null) throw new IllegalArgumentException("Missing authBlob!");
                            blob(username, Base64.getDecoder().decode(blob));
                            break;
                        case USER_PASS:
                            if (username == null) throw new IllegalArgumentException("Missing authUsername!");
                            if (password == null) throw new IllegalArgumentException("Missing authPassword!");
                            userPass(username, password);
                            break;
                        case ZEROCONF:
                            throw new IllegalStateException("Cannot handle ZEROCONF! Use ZeroconfServer.");
                        default:
                            throw new IllegalStateException("Unknown auth authStrategy: " + authConf.getStrategy());
                    }
                } else {
                    throw new IllegalStateException("Missing credentials!");
                }
            }

            session.connect();
            session.authenticate(loginCredentials, session.conf.getDeviceId());
            return session;
        }
    }

    public static class SpotifyAuthenticationException extends Exception {
        private SpotifyAuthenticationException(Keyexchange.APLoginFailed loginFailed) {
            super(loginFailed.getErrorCode().name());
        }
    }

    private static class Accumulator extends DataOutputStream {
        private byte[] bytes;

        Accumulator() {
            super(new ByteArrayOutputStream());
        }

        void dump() throws IOException {
            bytes = ((ByteArrayOutputStream) this.out).toByteArray();
            this.close();
        }

        @NotNull
        byte[] array() {
            return bytes;
        }
    }

    private class ConnectionHolder {
        final Socket socket;
        final DataInputStream in;
        final DataOutputStream out;

        ConnectionHolder(Socket socket) throws IOException {
            this.socket = socket;
            this.in = new DataInputStream(socket.getInputStream());
            this.out = new DataOutputStream(socket.getOutputStream());
        }
    }

    private class Receiver implements Runnable {
        private volatile boolean shouldStop = false;

        private Receiver() {
        }

        void stop() {
            shouldStop = true;
        }

        @Override
        public void run() {
            while (!shouldStop) {
                Packet packet;
                Packet.Type cmd;
                try {
                    packet = cipherPair.receiveEncoded(conn.in);
                    cmd = Packet.Type.parse(packet.cmd);
                    if (cmd == null) {
                        LOGGER.info(String.format("Skipping unknown command {cmd: 0x%s, payload: %s}", Integer.toHexString(packet.cmd), Utils.bytesToHex(packet.payload)));
                        continue;
                    }
                } catch (IOException | GeneralSecurityException ex) {
                    if (!shouldStop) {
                        LOGGER.fatal("Failed reading packet!", ex);
                        reconnect();
                    }

                    return;
                }

                switch (cmd) {
                    case Ping:
                        try {
                            long serverTime = new BigInteger(packet.payload).longValue();
                            TimeProvider.init((int) (serverTime - System.currentTimeMillis() / 1000));
                            send(Packet.Type.Pong, packet.payload);
                            LOGGER.trace(String.format("Handled Ping {payload: %s}", Utils.bytesToHex(packet.payload)));
                        } catch (IOException ex) {
                            LOGGER.fatal("Failed sending Pong!", ex);
                        }
                        break;
                    case PongAck:
                        LOGGER.trace(String.format("Handled PongAck {payload: %s}", Utils.bytesToHex(packet.payload)));
                        break;
                    case CountryCode:
                        countryCode = new String(packet.payload);
                        LOGGER.info("Received CountryCode: " + countryCode);
                        break;
                    case LicenseVersion:
                        ByteBuffer licenseVersion = ByteBuffer.wrap(packet.payload);
                        short id = licenseVersion.getShort();
                        byte[] buffer = new byte[licenseVersion.get()];
                        licenseVersion.get(buffer);
                        LOGGER.info(String.format("Received LicenseVersion: %d, %s", id, new String(buffer)));
                        break;
                    case Unknown_0x10:
                        LOGGER.debug("Received 0x10: " + Utils.bytesToHex(packet.payload));
                        break;
                    case MercurySub:
                    case MercuryUnsub:
                    case MercuryEvent:
                    case MercuryReq:
                        mercuryClient.dispatch(packet);
                        break;
                    case AesKey:
                    case AesKeyError:
                        audioKeyManager.dispatch(packet);
                        break;
                    case ChannelError:
                    case StreamChunkRes:
                        channelManager.dispatch(packet);
                        break;
                    default:
                        LOGGER.info("Skipping " + cmd.name());
                        break;
                }
            }
        }
    }
}
