/*
 * Copyright (c) 2013 Toni Spets <toni.spets@iki.fi>
 * 
 * Permission to use, copy, modify, and distribute this software for any
 * purpose with or without fee is hereby granted, provided that the above
 * copyright notice and this permission notice appear in all copies.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
 * WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR
 * ANY SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
 * WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
 * ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF
 * OR IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.
 */
package org.gexuy.cnc.tunnel;

import java.net.InetSocketAddress;
import java.util.List;

/**
 *
 * @author Toni Spets <toni.spets@iki.fi>
 */
public class Client {

    private short id;
    private InetSocketAddress address;
    private long lastPacket;
    private List<Short> friends;

    public Client(short id, List<Short> friends) { 
        this.id = id;
        this.lastPacket = System.currentTimeMillis();
        this.friends = friends;
    }

    public short getId() {
        return this.id;
    }

    public boolean isKnownClient(short otherId) {
        return friends.contains(otherId);
    }

    public void setAddress(InetSocketAddress newAddress) {
        address = newAddress;
    }

    public InetSocketAddress getAddress() {
        return address;
    }

    public void setLastPacket(long lastPacketReceived) {
        lastPacket = lastPacketReceived;
    }

    public long getLastPacket() {
        return lastPacket;
    }
    
}
