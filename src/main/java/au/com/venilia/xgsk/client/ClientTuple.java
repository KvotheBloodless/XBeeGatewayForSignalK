package au.com.venilia.xgsk.client;

public class ClientTuple {

	private final SignalKClient skClient;

	private final XBeeClient xbClient;

	protected ClientTuple(final SignalKClient skClient, final XBeeClient xbClient) {

		this.skClient = skClient;
		this.xbClient = xbClient;
	}

	public static ClientTuple of(final SignalKClient skClient, final XBeeClient xbClient) {

		return new ClientTuple(skClient, xbClient);
	}
}
