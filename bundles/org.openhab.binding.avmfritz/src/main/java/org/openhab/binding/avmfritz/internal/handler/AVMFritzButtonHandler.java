/**
 * Copyright (c) 2010-2020 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.avmfritz.internal.handler;

import static org.openhab.binding.avmfritz.internal.AVMFritzBindingConstants.*;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.avmfritz.internal.dto.AVMFritzBaseModel;
import org.openhab.binding.avmfritz.internal.dto.ButtonModel;
import org.openhab.binding.avmfritz.internal.dto.DeviceModel;
import org.openhab.core.library.types.DateTimeType;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.CommonTriggerEvents;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.types.UnDefType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handler for a FRITZ! buttons. Handles commands, which are sent to one of the channels.
 *
 * @author Christoph Weitkamp - Initial contribution
 */
@NonNullByDefault
public class AVMFritzButtonHandler extends DeviceHandler {

    private static final String TOP_RIGHT_SUFFIX = "-1";
    private static final String BOTTOM_RIGHT_SUFFIX = "-3";
    private static final String BOTTOM_LEFT_SUFFIX = "-5";
    private static final String TOP_LEFT_SUFFIX = "-7";

    private final Logger logger = LoggerFactory.getLogger(AVMFritzButtonHandler.class);
    /**
     * keeps track of the last timestamp for handling trigger events
     */
    private Instant lastTimestamp;

    /**
     * Constructor
     *
     * @param thing Thing object representing a FRITZ! button
     */
    public AVMFritzButtonHandler(Thing thing) {
        super(thing);
        lastTimestamp = Instant.now();
    }

    @Override
    public void onDeviceUpdated(ThingUID thingUID, AVMFritzBaseModel device) {
        if (thing.getUID().equals(thingUID)) {
            super.onDeviceUpdated(thingUID, device);

            if (device instanceof DeviceModel) {
                DeviceModel deviceModel = (DeviceModel) device;
                if (deviceModel.isHANFUNButton()) {
                    updateHANFUNButton(deviceModel.getButtons());
                }
                if (deviceModel.isButton()) {
                    if (DECT400_THING_TYPE.equals(thing.getThingTypeUID())) {
                        updateShortLongPressButton(deviceModel.getButtons());
                    } else if (DECT440_THING_TYPE.equals(thing.getThingTypeUID())) {
                        updateButtons(deviceModel.getButtons());
                    }
                    updateBattery(deviceModel);
                }
            }
        }
    }

    private void updateShortLongPressButton(List<ButtonModel> buttons) {
        ButtonModel shortPressButton = buttons.size() > 0 ? buttons.get(0) : null;
        ButtonModel longPressButton = buttons.size() > 1 ? buttons.get(1) : null;
        ButtonModel lastPressedButton = shortPressButton != null && (longPressButton == null
                || shortPressButton.getLastpressedtimestamp() > longPressButton.getLastpressedtimestamp())
                        ? shortPressButton
                        : longPressButton;
        if (lastPressedButton != null) {
            updateButton(lastPressedButton,
                    lastPressedButton.equals(shortPressButton) ? CommonTriggerEvents.SHORT_PRESSED
                            : CommonTriggerEvents.LONG_PRESSED);
        }
    }

    private void updateButtons(List<ButtonModel> buttons) {
        Optional<ButtonModel> topLeft = buttons.stream().filter(b -> b.getIdentifier().endsWith(TOP_LEFT_SUFFIX))
                .findFirst();
        if (topLeft.isPresent()) {
            updateButton(topLeft.get(), CommonTriggerEvents.PRESSED, CHANNEL_GROUP_TOP_LEFT);
        }
        Optional<ButtonModel> bottomLeft = buttons.stream().filter(b -> b.getIdentifier().endsWith(BOTTOM_LEFT_SUFFIX))
                .findFirst();
        if (bottomLeft.isPresent()) {
            updateButton(bottomLeft.get(), CommonTriggerEvents.PRESSED, CHANNEL_GROUP_BOTTOM_LEFT);
        }
        Optional<ButtonModel> topRight = buttons.stream().filter(b -> b.getIdentifier().endsWith(TOP_RIGHT_SUFFIX))
                .findFirst();
        if (topRight.isPresent()) {
            updateButton(topRight.get(), CommonTriggerEvents.PRESSED, CHANNEL_GROUP_TOP_RIGHT);
        }
        Optional<ButtonModel> bottomRight = buttons.stream()
                .filter(b -> b.getIdentifier().endsWith(BOTTOM_RIGHT_SUFFIX)).findFirst();
        if (bottomRight.isPresent()) {
            updateButton(bottomRight.get(), CommonTriggerEvents.PRESSED, CHANNEL_GROUP_BOTTOM_RIGHT);
        }
    }

    private void updateHANFUNButton(List<ButtonModel> buttons) {
        if (!buttons.isEmpty()) {
            updateButton(buttons.get(0), CommonTriggerEvents.PRESSED);
        }
    }

    private void updateButton(ButtonModel buttonModel, String event) {
        updateButton(buttonModel, event, null);
    }

    private void updateButton(ButtonModel buttonModel, String event, @Nullable String channelGroupId) {
        int lastPressedTimestamp = buttonModel.getLastpressedtimestamp();
        if (lastPressedTimestamp == 0) {
            updateThingChannelState(
                    channelGroupId == null ? CHANNEL_LAST_CHANGE
                            : channelGroupId + ChannelUID.CHANNEL_GROUP_SEPARATOR + CHANNEL_LAST_CHANGE,
                    UnDefType.UNDEF);
        } else {
            ZonedDateTime timestamp = ZonedDateTime.ofInstant(Instant.ofEpochSecond(lastPressedTimestamp),
                    ZoneId.systemDefault());
            Instant then = timestamp.toInstant();
            // Avoid dispatching events if "lastpressedtimestamp" is older than now "lastTimestamp" (e.g. during
            // restart)
            if (then.isAfter(lastTimestamp)) {
                lastTimestamp = then;
                triggerThingChannel(channelGroupId == null ? CHANNEL_PRESS
                        : channelGroupId + ChannelUID.CHANNEL_GROUP_SEPARATOR + CHANNEL_PRESS, event);
            }
            updateThingChannelState(
                    channelGroupId == null ? CHANNEL_LAST_CHANGE
                            : channelGroupId + ChannelUID.CHANNEL_GROUP_SEPARATOR + CHANNEL_LAST_CHANGE,
                    new DateTimeType(timestamp));
        }
    }

    /**
     * Triggers thing channels.
     *
     * @param channelId ID of the channel to be triggered.
     * @param event Event to emit
     */
    private void triggerThingChannel(String channelId, String event) {
        Channel channel = thing.getChannel(channelId);
        if (channel != null) {
            triggerChannel(channel.getUID(), event);
        } else {
            logger.debug("Channel '{}' in thing '{}' does not exist.", channelId, thing.getUID());
        }
    }
}
