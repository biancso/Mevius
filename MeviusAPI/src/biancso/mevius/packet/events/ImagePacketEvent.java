package biancso.mevius.packet.events;

import biancso.mevius.nio.MeviusClient;
import biancso.mevius.packet.MeviusImagePacket;
import biancso.mevius.packet.MeviusPacket;

public class ImagePacketEvent extends PacketEvent {

	public ImagePacketEvent(MeviusPacket packet, MeviusClient client, PacketEventType receive) {
		super(packet, client, receive);
		// TODO Auto-generated constructor stub
	}

	public MeviusImagePacket getPacket() {
		return getAlternativePacket();
	}
}
