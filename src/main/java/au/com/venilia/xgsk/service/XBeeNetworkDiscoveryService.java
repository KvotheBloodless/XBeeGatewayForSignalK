package au.com.venilia.xgsk.service;

import com.digi.xbee.api.XBeeDevice;

public interface XBeeNetworkDiscoveryService {

	/**
	 * Gets the local device
	 * 
	 * @return the localDevice
	 */
	public XBeeDevice getLocalInstance();

	/**
	 * Instructs discovery service to forget a failed node and cleanup resources.
	 * 
	 * @param nodeID the node identifier
	 */
	public void failedNode(final String nodeID);
}
