package net.tomp2p.holep;

import net.sctp4nat.core.SctpChannelFacade;
import net.tomp2p.connection.DefaultConnectionConfiguration;
import net.tomp2p.futures.BaseFutureAdapter;
import net.tomp2p.futures.FutureChannelCreator;
import net.tomp2p.futures.FutureDone;
import net.tomp2p.futures.Futures;
import net.tomp2p.message.Message;
import net.tomp2p.p2p.Peer;
import net.tomp2p.peers.PeerAddress;
import net.tomp2p.utils.Triple;
import net.tomp2p.utils.Utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is responsible for finding out the NATType which the peer is
 * using.
 * 
 * @author jonaswagner
 * @author Thomas Bocek
 * 
 */
public class NATTypeDetection {

	private static final Logger LOG = LoggerFactory.getLogger(NATTypeDetection.class);

	public static FutureDone<NATType> checkNATType(final Peer peer, final PeerAddress relayPeer) {
		return checkNATType(peer, relayPeer, 5);
	}

	/**
	 * This method contacts a Relay {@link Peer} in order to find out the NAT
	 * port assignment behavior. This assumes that you are behind a NAT as
	 * discovered with Peer.discover(). If you are not behind NAT, then this
	 * will return PORT_PRESERVING. There are 3 possible NAT behaviours: <br />
	 * PORT_PRESERVING = The NAT preserves the port which a peer uses to send
	 * messages from. <br />
	 * NON_PRESERVING_SEQUENTIAL = The NAT doesn't preserve the port and assigns
	 * another port in a sequential fashion (e.g. 1234). <br />
	 * NON_PRESERVING_OTHER = The NAT doesn't preserve the port and assigns
	 * another random port instead. <br />
	 * 
	 */
	public static FutureDone<NATType> checkNATType(final Peer peer, final PeerAddress relayPeer,
	        final int tolerance) {
		final FutureDone<NATType> futureDone = new FutureDone<NATType>();
		final FutureChannelCreator fcc1 = peer.connectionBean().reservation().create(3);
		fcc1.addListener(new BaseFutureAdapter<FutureChannelCreator>() {
			@Override
			public void operationComplete(FutureChannelCreator future) throws Exception {
				if (future.isSuccess()) {

					Triple<FutureDone<Message>, FutureDone<SctpChannelFacade>, FutureDone<Void>> futureResponse1 = peer.pingRPC().pingUDPDiscover(relayPeer, future.channelCreator(),
					        new DefaultConnectionConfiguration());
					Triple<FutureDone<Message>, FutureDone<SctpChannelFacade>, FutureDone<Void>> futureResponse2 = peer.pingRPC().pingUDPDiscover(relayPeer, future.channelCreator(),
					        new DefaultConnectionConfiguration());
					Triple<FutureDone<Message>, FutureDone<SctpChannelFacade>, FutureDone<Void>> futureResponse3 = peer.pingRPC().pingUDPDiscover(relayPeer, future.channelCreator(),
					        new DefaultConnectionConfiguration());

					FutureDone<FutureDone<Message>[]> fdd = Futures.whenAllSuccess(futureResponse1.first, futureResponse2.first,
					        futureResponse3.first);
					Utils.addReleaseListener(fcc1, fdd);
					fdd.addListener(new BaseFutureAdapter<FutureDone<FutureDone<Message>[]>>() {
						@Override
						public void operationComplete(FutureDone<FutureDone<Message>[]> future) throws Exception {
							if (future.isSuccess()) {
								if (future.object().length != 3) {
									futureDone.failed("expected exactly three futures");
									return;
								}
								if (!checkCompleteMessage(future.object()[0])) {
									futureDone.failed("expected filled message0");
									return;
								}
								if (!checkCompleteMessage(future.object()[1])) {
									futureDone.failed("expected filled message1");
									return;
								}
								if (!checkCompleteMessage(future.object()[2])) {
									futureDone.failed("expected filled message2");
									return;
								}

								final int seenAsPort1 = future.object()[0].object().intAt(0);
								final int seenAsPort2 = future.object()[1].object().intAt(0);
								final int seenAsPort3 = future.object()[2].object().intAt(0);

								final int actualPort1 = future.object()[0].object().recipientSocket().getPort();
								final int actualPort2 = future.object()[1].object().recipientSocket().getPort();
								final int actualPort3 = future.object()[2].object().recipientSocket().getPort();

								NATType natType = checkNATType(seenAsPort1, seenAsPort2, seenAsPort3, actualPort1,
								        actualPort2, actualPort3, tolerance);
								futureDone.done(natType);
							} else {
								futureDone.failed("expected two successful futures", future);
							}
						}

						private boolean checkCompleteMessage(FutureDone<Message> futureResponse) {
							Message message = futureResponse.object();
							if (message == null) {
								return false;
							}
							if (message.intAt(0) == null) {
								return false;
							}
							if (message.neighborsSet(0) == null) {
								return false;
							}
							if (message.neighborsSet(0).size() < 1) {
								return false;
							}
							if (message.recipientSocket() == null) {
								return false;
							}

							return true;
						}
					});

				} else {
					futureDone.failed("Could not emit NAT type! Channel creation failed", future);
				}
			}
		});
		return futureDone;
	}

	private static boolean twoOutOfThreeSame(int i1, int i2, int i3, int k1, int k2, int k3) {
		return (i1 == k1 && i2 == k2) || (i1 == k1 && i3 == k3) || (i2 == k2 && i3 == k3);
	}

	private static boolean sequential(int i1, int i2, int i3, int k1, int k2, int k3, int tolerance) {
		if (Math.abs(i1 - k1) < tolerance) {
			if (Math.abs(i2 - k2) < tolerance) {
				if (Math.abs(i3 - k3) < tolerance) {
					return true;
				}
			}
		}
		return false;
	}

	private static NATType checkNATType(final int seenAsPort1, final int seenAsPort2, final int seenAsPort3,
	        final int actualPort1, final int actualPort2, final int actualPort3, final int tolerance) {
		if (twoOutOfThreeSame(seenAsPort1, seenAsPort2, seenAsPort3, actualPort1, actualPort2, actualPort3)) {
			LOG.debug("Port preserving NAT detected. UDP hole punching is possible");
			return NATType.PORT_PRESERVING;
		} 
		if (sequential(seenAsPort1, seenAsPort2, seenAsPort3, actualPort1, actualPort2, actualPort3, tolerance)) {
			LOG.debug("NAT with sequential port multiplexing detected. UDP hole punching is still possible");
			return NATType.NON_PRESERVING_SEQUENTIAL;
		} 
		LOG.debug("Symmetric NAT detected (assumed since all other tests failed)");
		return NATType.NON_PRESERVING_OTHER;
	}
}
