package biancso.mevius.nio;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.StreamCorruptedException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Iterator;
import java.util.UUID;

import biancso.mevius.handler.ConnectionType;
import biancso.mevius.handler.MeviusHandler;
import biancso.mevius.nio.exceptions.MeviusCipherException;
import biancso.mevius.packet.MeviusPacket;
import biancso.mevius.packet.events.PacketEventType;
import biancso.mevius.utils.cipher.MeviusCipherKey;

public class MeviusClient {
	private final SocketChannel sc;
	private final MeviusHandler handler;
	private final UUID uuid;
	private final boolean self;
	private EventListener el;
	private PublicKey publickey;
	private PrivateKey privatekey;
	private long timeout = 10000;

	public MeviusClient(InetSocketAddress addr, MeviusHandler handler) throws IOException {
		this.sc = SocketChannel.open(addr);
		sc.configureBlocking(false);
		this.handler = handler;
		(el = new EventListener(sc)).start();
		uuid = UUID.randomUUID();
		self = true;
		KeyPair kp = MeviusCipherKey.randomRSAKeyPair(512).getKey();
		privatekey = kp.getPrivate();
		sc.write(convert(kp.getPublic()));
		handler.connection(ConnectionType.CLIENT_CONNECT_TO_SERVER, this);
	}

	public MeviusClient(SocketChannel channel, PublicKey publickey, MeviusHandler handler) {
		this.sc = channel;
		this.handler = handler;
		handler.getClientHandler().join(this);
		uuid = UUID.randomUUID();
		self = false;
		this.publickey = publickey;
	}

	public SocketChannel getSocketChannel() {
		return sc;
	}

	public boolean isReady() {
		return self ? publickey != null : handler.getClientHandler().getPublicKey(this) != null;
	}

	protected void setPublicKey(PublicKey publickey) {
		this.publickey = publickey;
	}

	public final PublicKey getPublicKey() {
		return publickey;
	}

	public final PrivateKey getPrivateKey() {
		return privatekey;
	}

	public boolean isClosed() {
		return !sc.isOpen(); // Configure out is socket closed
	}

	public final UUID getUUID() {
		return this.uuid; // get UniqueId
	}

	public void disconnect() throws IOException {
		if (self && el != null)
			el.interrupt();
		sc.close();
		if (!self)
			handler.getClientHandler().exit(this);
		handler.connection(ConnectionType.CLIENT_DISCONNECT_FROM_SERVER, this);
	}

	public void sendPacket(MeviusPacket packet) throws IOException {
		if (!packet.isSigned())
			throw new IllegalStateException(new Throwable("Packet is not signed!"));
		try {
			sc.write(convert(packet));
			handler.callEvent(MeviusHandler.getPacketEventInstance(packet, this, PacketEventType.SEND));
		} catch (MeviusCipherException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private ByteBuffer convert(Object obj) throws IOException, MeviusCipherException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		ObjectOutputStream oos = new ObjectOutputStream(baos);
		oos.flush();
		oos.writeObject(obj);
		oos.flush();
		return ByteBuffer.wrap(baos.toByteArray());
	}

	public MeviusHandler getHandler() {
		return handler; // return handler
	}

	public InetAddress getInetAddress() {
		return sc.socket().getInetAddress();
	}

	public void setConnectionTimeout(int timeout) {
		this.timeout = timeout * 1000;
	}

	public void setConnectionTimeout(long timeout) {
		this.timeout = timeout;
	}

	public long getConnectionTimeout() {
		return timeout;
	}

	class EventListener extends Thread {
		private final Selector selector;

		public EventListener(SocketChannel channel) throws IOException {
			selector = Selector.open();
			channel.register(selector, SelectionKey.OP_READ);
		}

		public void run() {
			while (true) {
				try {
					selector.select();
					Iterator<SelectionKey> it = selector.selectedKeys().iterator();
					while (it.hasNext()) {
						SelectionKey k = it.next();
						it.remove();
						if (!k.isValid())
							continue;
						if (k.isReadable()) {
							read(k);
						}
						continue;
					}
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}

		private void read(SelectionKey k) {
			try {
				SocketChannel channel = (SocketChannel) k.channel();
				ByteBuffer data = ByteBuffer.allocate(100000);
				data.clear();
				channel.read(data);
				ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(data.array()));
				Object obj = ois.readObject();
				if (!isReady() && !(obj instanceof PublicKey))
					return;
				if (obj instanceof PublicKey) {
					setPublicKey((PublicKey) obj);
					return;
				}
				if (!(obj instanceof MeviusPacket))
					return;
				MeviusPacket packet = (MeviusPacket) obj;
				handler.callEvent(MeviusHandler.getPacketEventInstance(packet,
						handler.getClientHandler().getClient(channel.socket().getInetAddress().getHostAddress()),
						PacketEventType.RECEIVE));
			} catch (IOException | ClassNotFoundException e) {
				e.printStackTrace();
				if (e.getClass().equals(StreamCorruptedException.class))
					try {
						disconnect();
					} catch (IOException e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					}
			}
		}
	}

}
