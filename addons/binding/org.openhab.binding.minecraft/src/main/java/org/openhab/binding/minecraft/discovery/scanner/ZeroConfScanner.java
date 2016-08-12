/**
 * Copyright (c) 2014-2016 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.minecraft.discovery.scanner;

import java.io.IOException;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceListener;

import org.openhab.binding.minecraft.discovery.MinecraftDiscoveryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import rx.Observable;
import rx.Observable.OnSubscribe;
import rx.Subscriber;
import rx.functions.Action0;
import rx.subscriptions.Subscriptions;

/**
 * Finds minecraft servers through zeroconf.
 *
 * @author Mattias Markehed
 */
public class ZeroConfScanner {

    private static final Logger logger = LoggerFactory.getLogger(MinecraftDiscoveryService.class);

    private ZeroConfScanner() {
    }

    /**
     * Return an observable that scans for Minecraft servers.
     *
     * @return observable emitting server items.
     */
    public static Observable<ServiceEvent> create() {
        return Observable.<ServiceEvent>create(new OnSubscribe<ServiceEvent>() {

            @Override
            public void call(final Subscriber<? super ServiceEvent> subscriber) {

                if (!subscriber.isUnsubscribed()) {
                    final ServiceListener listener = new ServiceListener() {
                        @Override
                        public void serviceResolved(ServiceEvent event) {
                        }

                        @Override
                        public void serviceRemoved(ServiceEvent event) {
                        }

                        @Override
                        public void serviceAdded(ServiceEvent event) {

                            if (!subscriber.isUnsubscribed()) {
                                subscriber.onNext(event);
                            }
                        }
                    };

                    try {
                        final JmDNS jmdns = JmDNS.create();
                        jmdns.addServiceListener("_http._tcp.local.", listener);
                        subscriber.add(Subscriptions.create(new Action0() {

                            @Override
                            public void call() {
                                if (jmdns != null) {
                                    jmdns.unregisterAllServices();
                                    try {
                                        jmdns.close();
                                    } catch (IOException ex) {
                                        logger.error("Error closing jmdns: {}", ex.getMessage(), ex);
                                    }
                                }
                            }
                        }));
                    } catch (IOException ex) {
                        logger.error("Error waiting for device discovery scan: {}", ex.getMessage(), ex);
                    }
                }
            }
        }).distinctUntilChanged();
    }
}
