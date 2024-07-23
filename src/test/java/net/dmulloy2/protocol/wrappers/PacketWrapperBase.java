package net.dmulloy2.protocol.wrappers;

import com.comphenix.protocol.events.PacketContainer;

public abstract class PacketWrapperBase {
    protected final PacketContainer container;

    protected PacketWrapperBase(PacketContainer container) {
        this.container = container;
    }
}
