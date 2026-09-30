package com.cinescout.imagery;

import io.netty.resolver.AddressResolver;
import io.netty.resolver.AddressResolverGroup;
import io.netty.resolver.InetNameResolver;
import io.netty.resolver.InetSocketAddressResolver;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Promise;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

/**
 * Resolves host names for the HTTP client and refuses any that lead to a local or private address. Checking a
 * name before a request is not enough on its own: the name could resolve differently a moment later, when the
 * connection is made ("DNS rebinding"). Here the check is on the very addresses the client connects to.
 */
public final class PublicOnlyResolverGroup extends AddressResolverGroup<InetSocketAddress> {

    public static final PublicOnlyResolverGroup INSTANCE = new PublicOnlyResolverGroup();

    private PublicOnlyResolverGroup() {
    }

    @Override
    protected AddressResolver<InetSocketAddress> newResolver(EventExecutor executor) {
        return new InetSocketAddressResolver(executor, new PublicNameResolver(executor));
    }

    private static final class PublicNameResolver extends InetNameResolver {

        PublicNameResolver(EventExecutor executor) {
            super(executor);
        }

        @Override
        protected void doResolve(String host, Promise<InetAddress> promise) {
            try {
                InetAddress address = InetAddress.getByName(host);
                if (PublicAddresses.isPublic(address)) {
                    promise.setSuccess(address);
                } else {
                    promise.setFailure(new UnknownHostException(host + " is not a public address"));
                }
            } catch (UnknownHostException e) {
                promise.setFailure(e);
            }
        }

        @Override
        protected void doResolveAll(String host, Promise<List<InetAddress>> promise) {
            try {
                List<InetAddress> addresses = new ArrayList<>();
                for (InetAddress address : InetAddress.getAllByName(host)) {
                    if (!PublicAddresses.isPublic(address)) {
                        promise.setFailure(new UnknownHostException(host + " is not a public address"));
                        return;
                    }
                    addresses.add(address);
                }
                promise.setSuccess(addresses);
            } catch (UnknownHostException e) {
                promise.setFailure(e);
            }
        }
    }
}
