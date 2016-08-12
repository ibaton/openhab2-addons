/**
 * Copyright (c) 2014-2016 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.minecraft.discovery;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.jmdns.ServiceEvent;

import org.eclipse.smarthome.config.discovery.AbstractDiscoveryService;
import org.eclipse.smarthome.config.discovery.DiscoveryResult;
import org.eclipse.smarthome.config.discovery.DiscoveryResultBuilder;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.openhab.binding.minecraft.MinecraftBindingConstants;
import org.openhab.binding.minecraft.config.ServerConfig;
import org.openhab.binding.minecraft.discovery.scanner.ZeroConfScanner;
import org.openhab.binding.minecraft.handler.MinecraftServerHandler;
import org.openhab.binding.minecraft.handler.server.ServerConnection;
import org.openhab.binding.minecraft.internal.MinecraftHandlerFactory;
import org.openhab.binding.minecraft.message.data.PlayerData;
import org.openhab.binding.minecraft.message.data.SignData;
import org.openhab.binding.minecraft.util.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import rx.Observable;
import rx.Subscription;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.functions.Func2;
import rx.schedulers.Schedulers;
import rx.subscriptions.CompositeSubscription;

/**
 * Handles discovery of Minecraft server, players and signs.
 *
 * @author Mattias Markehed
 */
public class MinecraftDiscoveryService extends AbstractDiscoveryService {

    private final Logger logger = LoggerFactory.getLogger(MinecraftDiscoveryService.class);

    private static final int DISCOVER_TIMEOUT_SECONDS = 60;

    private CompositeSubscription subscription;

    public MinecraftDiscoveryService() {
        super(MinecraftBindingConstants.SUPPORTED_THING_TYPES_UIDS, DISCOVER_TIMEOUT_SECONDS, false);
    }

    @Override
    public Set<ThingTypeUID> getSupportedThingTypes() {
        return MinecraftBindingConstants.SUPPORTED_THING_TYPES_UIDS;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void startScan() {
        logger.debug("Starting Minecraft discovery scan");
        discoverServers();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected synchronized void stopScan() {
        logger.debug("Stopping Minecraft discovery scan");
        stopServerDiscovery();
        super.stopScan();
    }

    /**
     * Setup subscribers searching for servers.
     * Start scanning for items on servers.
     */
    private void discoverServers() {
        subscription = new CompositeSubscription();

        Observable<ServerConnection> serverRx = serversConnectRx().cache();

        Subscription scanSubscriber = ZeroConfScanner.create().filter(new Func1<ServiceEvent, Boolean>() {
            @Override
            public Boolean call(ServiceEvent event) {
                return "wc-minecraft".equals(event.getName());
            }
        }).subscribeOn(Schedulers.newThread()).subscribeOn(Schedulers.newThread())
                .subscribe(new Action1<ServiceEvent>() {
                    @Override
                    public void call(ServiceEvent event) {
                        int port = event.getInfo().getPort();
                        String host = event.getInfo().getInetAddresses()[0].getHostAddress();
                        submitServerDiscoveryResults(host, port);
                    }
                }, new Action1<Throwable>() {

                    @Override
                    public void call(Throwable e) {
                        logger.error("Error while scanning for server", e);
                    }
                });

        Subscription playerSubscription = serverRx
                .flatMap(new Func1<ServerConnection, Observable<? extends List<PlayerData>>>() {
                    @Override
                    public Observable<List<PlayerData>> call(ServerConnection socketHandler) {
                        return socketHandler.getSocketHandler().getPlayersRx().distinct();
                    }
                }, new Func2<ServerConnection, List<PlayerData>, Pair<ServerConnection, List<PlayerData>>>() {
                    @Override
                    public Pair<ServerConnection, List<PlayerData>> call(ServerConnection connection,
                            List<PlayerData> players) {
                        return new Pair<ServerConnection, List<PlayerData>>(connection, players);
                    }
                }).subscribeOn(Schedulers.newThread())
                .subscribe(new Action1<Pair<ServerConnection, List<PlayerData>>>() {
                    @Override
                    public void call(Pair<ServerConnection, List<PlayerData>> conectionPlayerPair) {
                        for (PlayerData player : conectionPlayerPair.second) {
                            submitPlayerDiscoveryResults(conectionPlayerPair.first.getThingUID(), player.getName());
                        }
                    }
                }, new Action1<Throwable>() {

                    @Override
                    public void call(Throwable e) {
                        logger.error("Error while scanning for players", e);
                    }
                });

        Subscription signSubscription = serverRx.flatMap(new Func1<ServerConnection, Observable<List<SignData>>>() {
            @Override
            public Observable<List<SignData>> call(ServerConnection connection) {
                return connection.getSocketHandler().getSignsRx().distinct();
            }
        }, new Func2<ServerConnection, List<SignData>, Pair<ServerConnection, List<SignData>>>() {
            @Override
            public Pair<ServerConnection, List<SignData>> call(ServerConnection connection, List<SignData> signs) {
                return new Pair<ServerConnection, List<SignData>>(connection, signs);
            }
        }).subscribe(new Action1<Pair<ServerConnection, List<SignData>>>() {
            @Override
            public void call(Pair<ServerConnection, List<SignData>> conectionSignPair) {
                for (SignData sign : conectionSignPair.second) {
                    submitSignDiscoveryResults(conectionSignPair.first.getThingUID(), sign);
                }
            }
        }, new Action1<Throwable>() {
            @Override
            public void call(Throwable e) {
                logger.error("Error while scanning for signs", e);
            }
        });

        subscription.add(playerSubscription);
        subscription.add(signSubscription);
        subscription.add(scanSubscriber);
    }

    /**
     * Teardown subscribers and stop searching for server and server items.
     */
    private void stopServerDiscovery() {
        if (subscription != null && !subscription.isUnsubscribed()) {
            subscription.unsubscribe();
        }
    }

    /**
     * Get all servers that have been added as observable.
     *
     * @return observable emitting server objects.
     */
    private Observable<ServerConnection> serversConnectRx() {
        return Observable.from(MinecraftHandlerFactory.getMinecraftServers())
                .flatMap(new Func1<MinecraftServerHandler, Observable<ServerConnection>>() {
                    @Override
                    public Observable<ServerConnection> call(MinecraftServerHandler server) {
                        ServerConfig config = server.getServerConfig();
                        return ServerConnection.create(server.getThing().getUID(), config.getHostname(),
                                config.getPort());
                    }
                });
    }

    /**
     * Submit the discovered Devices to the Smarthome inbox,
     *
     * @param ip The Device IP
     */
    private void submitServerDiscoveryResults(String ip, int port) {

        // uid must not contains dots
        ThingUID uid = new ThingUID(MinecraftBindingConstants.THING_TYPE_SERVER, ip.replace('.', '_'));

        boolean serverAlreadyAdded = false;
        for (MinecraftServerHandler handler : MinecraftHandlerFactory.getMinecraftServers()) {
            if (handler.getThing().getUID().equals(uid)) {
                serverAlreadyAdded = true;
            }
        }

        if (uid != null && !serverAlreadyAdded) {
            Map<String, Object> properties = new HashMap<>();
            properties.put(MinecraftBindingConstants.PARAMETER_HOSTNAME, ip);
            properties.put(MinecraftBindingConstants.PARAMETER_PORT, port);
            DiscoveryResult result = DiscoveryResultBuilder.create(uid).withProperties(properties)
                    .withLabel("Minecraft Server (" + ip + ")").build();
            thingDiscovered(result);
        }
    }

    /**
     * Submit the discovered Devices to the Smarthome inbox,
     *
     * @param ip The Device IP
     */
    private void submitPlayerDiscoveryResults(ThingUID bridgeUID, String name) {

        // uid must not contains dots
        ThingUID uid = new ThingUID(MinecraftBindingConstants.THING_TYPE_PLAYER, bridgeUID.hashCode() + "_" + name);

        if (uid != null) {
            Map<String, Object> properties = new HashMap<>();
            properties.put(MinecraftBindingConstants.PARAMETER_PLAYER_NAME, name);
            DiscoveryResult result = DiscoveryResultBuilder.create(uid).withProperties(properties).withBridge(bridgeUID)
                    .withLabel("Minecraft Player (" + name + ")").build();
            thingDiscovered(result);
        }
    }

    /**
     * Submit the discovered Signs to the Smarthome inbox,
     *
     * @param serverName name of server
     * @param sign data describing sign
     */
    private void submitSignDiscoveryResults(ThingUID bridgeUID, SignData sign) {

        // uid must not contains dots
        ThingUID uid = new ThingUID(MinecraftBindingConstants.THING_TYPE_SIGN,
                bridgeUID.hashCode() + "_" + sign.getName());

        if (uid != null) {
            Map<String, Object> properties = new HashMap<>();
            properties.put(MinecraftBindingConstants.PARAMETER_SIGN_NAME, sign.getName());
            DiscoveryResult result = DiscoveryResultBuilder.create(uid).withProperties(properties).withBridge(bridgeUID)
                    .withLabel("Minecraft Sign (" + sign.getName() + ")").build();
            thingDiscovered(result);
        }
    }
}
