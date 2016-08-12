/**
 * Copyright (c) 2014 openHAB UG (haftungsbeschraenkt) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.minecraft.handler;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.eclipse.smarthome.core.library.types.DecimalType;
import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.library.types.StringType;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandler;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.State;
import org.openhab.binding.minecraft.MinecraftBindingConstants;
import org.openhab.binding.minecraft.config.ServerConfig;
import org.openhab.binding.minecraft.handler.server.ServerConnection;
import org.openhab.binding.minecraft.message.data.PlayerData;
import org.openhab.binding.minecraft.message.data.ServerData;
import org.openhab.binding.minecraft.message.data.SignData;
import org.openhab.binding.minecraft.util.RetryWithDelay;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import rx.Observable;
import rx.Subscription;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.subscriptions.CompositeSubscription;

/**
 * The {@link MinecraftServerHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Mattias Markehed - Initial contribution
 */
public class MinecraftServerHandler extends BaseThingHandler {

    private Logger logger = LoggerFactory.getLogger(MinecraftServerHandler.class);

    private ServerConfig config;

    private Observable<ServerConnection> serverConnectionRX;
    private Subscription subscription;

    public MinecraftServerHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void initialize() {
        super.initialize();
        config = getConfigAs(ServerConfig.class);
        logger.info("Initializing MinecraftHandler");
        connectToServer();
    }

    /**
     * Get server configuration
     *
     * @return
     */
    public ServerConfig getServerConfig() {
        return config;
    }

    /**
     * Directly connect to server.
     * Reconnects when connection is lost
     */
    private void connectToServer() {
        String host = config.getHostname();
        int port = config.getPort();

        subscription = new CompositeSubscription();

        serverConnectionRX = ServerConnection.create(getThing().getUID(), host, port)
                .doOnNext(new Action1<ServerConnection>() {
                    @Override
                    public void call(ServerConnection item) {
                        updateOnlineState(true);
                    }
                }).doOnError(new Action1<Throwable>() {

                    @Override
                    public void call(Throwable e) {
                        updateOnlineState(false);
                    }
                }).retryWhen(new RetryWithDelay(1, TimeUnit.MINUTES)).repeat().replay(1).refCount();

        subscription = serverConnectionRX.flatMap(new Func1<ServerConnection, Observable<ServerData>>() {

            @Override
            public Observable<ServerData> call(ServerConnection connection) {
                return connection.getSocketHandler().getServerRx();
            }
        }).subscribe(new Action1<ServerData>() {

            @Override
            public void call(ServerData serverData) {
                updateServerState(serverData);
            }
        });
    }

    public Observable<List<SignData>> getSignsRx() {
        return serverConnectionRX.flatMap(new Func1<ServerConnection, Observable<List<SignData>>>() {

            @Override
            public Observable<List<SignData>> call(ServerConnection connection) {
                return connection.getSocketHandler().getSignsRx();
            }
        });
    }

    public Observable<List<PlayerData>> getPlayerRx() {
        return serverConnectionRX.flatMap(new Func1<ServerConnection, Observable<List<PlayerData>>>() {
            @Override
            public Observable<List<PlayerData>> call(ServerConnection connection) {
                return connection.getSocketHandler().getPlayersRx();
            }
        });
    }

    /**
     * Update online state of server
     *
     * @param isOnline true if server is online
     */
    private void updateOnlineState(boolean isOnline) {
        State onlineState = isOnline ? OnOffType.ON : OnOffType.OFF;
        updateState(MinecraftBindingConstants.CHANNEL_ONLINE, onlineState);
    }

    /**
     * Update state of minecraft server
     *
     * @param serverData
     */
    private void updateServerState(ServerData serverData) {
        State serverNameState = new StringType(serverData.getName());
        State playersState = new DecimalType(serverData.getPlayers());
        State maxPlayersState = new DecimalType(serverData.getMaxPlayers());
        State versionState = new StringType(serverData.getVersion());
        State bukkitVersionState = new StringType(serverData.getBukkitVersion());

        updateState(MinecraftBindingConstants.CHANNEL_NAME, serverNameState);
        updateState(MinecraftBindingConstants.CHANNEL_PLAYERS, playersState);
        updateState(MinecraftBindingConstants.CHANNEL_MAX_PLAYERS, maxPlayersState);
        updateState(MinecraftBindingConstants.CHANNEL_VERSION, versionState);
        updateState(MinecraftBindingConstants.CHANNEL_BUKKIT_VERSION, bukkitVersionState);
    }

    @Override
    public void dispose() {
        logger.info("Disposing minecraft server thing");
        subscription.unsubscribe();
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
    }
}
