package io.synadia.client.support;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Interface to implement to provide a InetAddress implementation
 */
public interface NatsInetAddressProvider {
    /**
     * Creates an InetAddress based on the provided host name and IP address.
     * @param host the specified host
     * @param addr the raw IP address in network byte order
     * @return  an InetAddress object created from the raw IP address.
     * @throws     UnknownHostException  if IP address is of illegal length
     */
    default InetAddress getByAddress(String host, byte[] addr) throws UnknownHostException {
        return InetAddress.getByAddress(host, addr);
    }

    /**
     * Determines the IP address of a host, given the host's name.
     * @param      host   the specified host, or {@code null}.
     * @return     an IP address for the given host name.
     * @throws     UnknownHostException  if no IP address for the
     *               {@code host} could be found, or if a scope_id was specified
     *               for a global IPv6 address.
     * @throws     SecurityException if a security manager exists
     *             and its checkConnect method doesn't allow the operation
     */
    default InetAddress getByName(String host) throws UnknownHostException {
        return InetAddress.getByName(host);
    }

    /**
     * Given the name of a host, returns an array of its IP addresses,
     * @param      host   the name of the host, or {@code null}.
     * @return     an array of all the IP addresses for a given host name.
     *
     * @throws     UnknownHostException  if no IP address for the
     *               {@code host} could be found, or if a scope_id was specified
     *               for a global IPv6 address.
     * @throws     SecurityException  if a security manager exists and its
     *               {@code checkConnect} method doesn't allow the operation.
     */
    default InetAddress[] getAllByName(String host) throws UnknownHostException {
        return InetAddress.getAllByName(host);
    }

    /**
     * Returns the loopback address.
     * @return  the InetAddress loopback instance.
     */
    default InetAddress getLoopbackAddress() {
        return InetAddress.getLoopbackAddress();
    }

    /**
     * Returns an {@code InetAddress} object given the raw IP address .
     * @param addr the raw IP address in network byte order
     * @return  an InetAddress object created from the raw IP address.
     * @throws     UnknownHostException  if IP address is of illegal length
     */
    default InetAddress getByAddress(byte[] addr) throws UnknownHostException {
        return InetAddress.getByAddress(addr);
    }

    /**
     * Returns the address of the local host.
     * @return     the address of the local host.
     * @throws     UnknownHostException  if the local host name could not
     *             be resolved into an address.
     */
    default InetAddress getLocalHost() throws UnknownHostException {
        return InetAddress.getLocalHost();
    }
}
