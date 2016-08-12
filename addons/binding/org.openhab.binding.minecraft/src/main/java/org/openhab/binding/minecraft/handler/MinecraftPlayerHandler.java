/**
 * Copyright (c) 2014 openHAB UG (haftungsbeschraenkt) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.minecraft.handler;

import java.util.List;

import org.eclipse.smarthome.core.library.types.DecimalType;
import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.library.types.StringType;
import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandler;
import org.eclipse.smarthome.core.thing.binding.ThingHandler;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.State;
import org.openhab.binding.minecraft.MinecraftBindingConstants;
import org.openhab.binding.minecraft.config.PlayerConfig;
import org.openhab.binding.minecraft.message.data.PlayerData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import rx.Observable;
import rx.Subscription;
import rx.functions.Action1;
import rx.functions.Func1;

/**
 * The {@link MinecraftPlayerHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Mattias Markehed - Initial contribution
 */
public class MinecraftPlayerHandler extends BaseThingHandler {

    private Logger logger = LoggerFactory.getLogger(MinecraftPlayerHandler.class);

    private Subscription playerSubscription;
    private MinecraftServerHandler bridgeHandler;
    private PlayerConfig config;

    public MinecraftPlayerHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void initialize() {
        this.bridgeHandler = getBridgeHandler();
        this.config = getThing().getConfiguration().as(PlayerConfig.class);

        if (getThing().getBridgeUID() == null) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "No bridge configured");

            return;
        }

        updateStatus(ThingStatus.ONLINE);
        hookupListeners(bridgeHandler);
    }

    @Override
    public void dispose() {
        super.dispose();
        if (!playerSubscription.isUnsubscribed()) {
            playerSubscription.unsubscribe();
        }
    }

    private void hookupListeners(MinecraftServerHandler bridgeHandler) {
        playerSubscription = bridgeHandler.getPlayerRx().doOnNext(new Action1<List<PlayerData>>() {
            @Override
            public void call(List<PlayerData> players) {

                boolean playerOnline = false;
                for (PlayerData player : players) {
                    if (config.getName().equals(player.getName())) {
                        playerOnline = true;
                        break;
                    }
                }
                State onlineState = playerOnline ? OnOffType.ON : OnOffType.OFF;
                updateState(MinecraftBindingConstants.CHANNEL_PLAYER_ONLINE, onlineState);
            }
        }).flatMap(new Func1<List<PlayerData>, Observable<PlayerData>>() {
            @Override
            public Observable<PlayerData> call(List<PlayerData> players) {
                return Observable.from(players);
            }
        }).filter(new Func1<PlayerData, Boolean>() {
            @Override
            public Boolean call(PlayerData player) {
                return config.getName().equals(player.getName());
            }
        }).subscribe(new Action1<PlayerData>() {

            @Override
            public void call(PlayerData player) {
                updatePlayerState(player);
            }
        });
    }

    /**
     * Updates the state of player
     *
     * @param player the player to update
     */
    private void updatePlayerState(PlayerData player) {
        State playerNameState = new StringType(player.getDisplayName());
        State playerLevel = new DecimalType(player.getLevel());
        State playerLevelPercentage = new DecimalType(player.getExperience());
        State playerTotalExperience = new DecimalType(player.getTotalExperience());
        State playerHealth = new DecimalType(player.getHealth());
        State playerWalkSpeed = new DecimalType(player.getWalkSpeed());
        State playerLocationX = new DecimalType(player.getLocation().getX());
        State playerLocationY = new DecimalType(player.getLocation().getY());
        State playerLocationZ = new DecimalType(player.getLocation().getZ());

        updateState(MinecraftBindingConstants.CHANNEL_PLAYER_NAME, playerNameState);
        updateState(MinecraftBindingConstants.CHANNEL_PLAYER_LEVEL_PERCENTAGE, playerLevelPercentage);
        updateState(MinecraftBindingConstants.CHANNEL_PLAYER_TOTAL_EXPERIENCE, playerTotalExperience);
        updateState(MinecraftBindingConstants.CHANNEL_PLAYER_LEVEL, playerLevel);
        updateState(MinecraftBindingConstants.CHANNEL_PLAYER_HEALTH, playerHealth);
        updateState(MinecraftBindingConstants.CHANNEL_PLAYER_WALK_SPEED, playerWalkSpeed);
        updateState(MinecraftBindingConstants.CHANNEL_PLAYER_LOCATION_X, playerLocationX);
        updateState(MinecraftBindingConstants.CHANNEL_PLAYER_LOCATION_Y, playerLocationY);
        updateState(MinecraftBindingConstants.CHANNEL_PLAYER_LOCATION_Z, playerLocationZ);
    }

    private synchronized MinecraftServerHandler getBridgeHandler() {

        Bridge bridge = getBridge();
        if (bridge == null) {
            logger.debug("Required bridge not defined for device {}.");
            return null;
        } else {
            return getBridgeHandler(bridge);
        }

    }

    private synchronized MinecraftServerHandler getBridgeHandler(Bridge bridge) {

        MinecraftServerHandler bridgeHandler = null;

        ThingHandler handler = bridge.getHandler();
        if (handler instanceof MinecraftServerHandler) {
            bridgeHandler = (MinecraftServerHandler) handler;
        } else {
            logger.debug("No available bridge handler found yet. Bridge: {} .", bridge.getUID());
            bridgeHandler = null;
        }
        return bridgeHandler;
    }

    @Override
    public void updateState(String channelID, State state) {
        ChannelUID channelUID = new ChannelUID(this.getThing().getUID(), channelID);
        updateState(channelUID, state);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
    }
}
